package com.chatgpt.memory;

import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ConversationPairDetail;
import com.chatgpt.memory.model.VectorSegmentationResult;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.VectorSessionSplitter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.List;

@Slf4j
@SpringBootTest
public class ReparseAttachmentsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatExportParser chatExportParser;

    @Autowired
    private VectorSessionSplitter vectorSessionSplitter;

    @Autowired
    private SessionBoundaryPairMapper sessionBoundaryPairMapper;

    @Test
    public void updateExistingPairsWithAttachments() throws Exception {
        // 1. 将旧的 VARCHAR(512) 物理修改为 TEXT，防止数据截断
        try {
            jdbcTemplate.execute("ALTER TABLE `session_boundary_pair` MODIFY COLUMN `message_a_text` TEXT NOT NULL");
            jdbcTemplate.execute("ALTER TABLE `session_boundary_pair` MODIFY COLUMN `message_b_text` TEXT NOT NULL");
            log.info("Successfully modified message_a_text and message_b_text to TEXT.");
        } catch (Exception e) {
            log.warn("Modify column failed: {}", e.getMessage());
        }

        // 2. 重新扫描全量 1,142 场对话中的多模态图片指针并同步刷入 MySQL
        File file = new File("e:/data/chatGPT_back/chatGPT导出20251214/chat.html");
        List<ChatConversation> conversations = chatExportParser.parseHtmlFile(file);

        int updatedCount = 0;
        int imgPairsFound = 0;

        for (ChatConversation conv : conversations) {
            VectorSegmentationResult result = vectorSessionSplitter.inspectConversation(conv);
            if (result == null || result.getPairDetails() == null) continue;

            for (ConversationPairDetail pair : result.getPairDetails()) {
                String textA = pair.getTextA();
                String textB = pair.getTextB();

                boolean hasAtt = (textA != null && (textA.contains("![图片]") || textA.contains("/chat-assets/")))
                              || (textB != null && (textB.contains("![图片]") || textB.contains("/chat-assets/")));

                if (hasAtt) {
                    imgPairsFound++;
                    log.info("[ImageFound] Conv: {}, Pair #{}: textA_has_img={}, textB_has_img={}",
                            conv.getConversationId(), pair.getIndex(),
                            textA != null && textA.contains("![图片]"),
                            textB != null && textB.contains("![图片]"));

                    int rows = sessionBoundaryPairMapper.updatePairTextAndAttachment(
                            conv.getConversationId(),
                            pair.getIndex(),
                            textA,
                            textB,
                            1
                    );
                    if (rows > 0) updatedCount++;
                }
            }
        }

        log.info("Finished scanning conversations. Total image pairs found: {}, MySQL rows updated: {}", imgPairsFound, updatedCount);
    }
}
