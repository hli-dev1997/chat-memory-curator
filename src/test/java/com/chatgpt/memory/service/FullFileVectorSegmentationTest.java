package com.chatgpt.memory.service;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import dev.langchain4j.model.embedding.BgeSmallZhEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对整份导出文件 (chat.html) 的 BGE 向量语义全量切分与报告生成测试
 * <p>
 * 验证对 87MB 真实导出数据使用 BGE 向量初筛 (0.82 / 0.60 门限, 短文本 30 字) 跑通全量物理切分，
 * 并打印出切片后的样例 Session 文档结构。
 * </p>
 *
 * @author Antigravity
 */
class FullFileVectorSegmentationTest {

    private static final Logger log = LoggerFactory.getLogger(FullFileVectorSegmentationTest.class);

    private static final String REAL_DATA_PATH = "e:/data/chatGPT_back/chatGPT导出20251214/chat.html";

    private ChatExportParser parser;
    private VectorSessionSplitter vectorSplitter;

    @BeforeEach
    void setUp() {
        parser = new ChatExportParser();
        final EmbeddingModel embeddingModel = new BgeSmallZhEmbeddingModel();
        vectorSplitter = new VectorSessionSplitter(embeddingModel, 0.82, 0.60, 30);
    }

    @Test
    @DisplayName("对全量 chat.html 进行 BGE 向量语义切分并输出文档总结清单")
    void testFullFileVectorSegmentation() throws IOException {
        final File htmlFile = new File(REAL_DATA_PATH);
        if (!htmlFile.exists()) {
            log.warn("未找到数据文件: {}，跳过测试", REAL_DATA_PATH);
            return;
        }

        log.info("==========================================================================================");
        log.info("                 【开始对全量 chat.html 进行 BGE 向量语义初筛切分】");
        log.info("==========================================================================================");

        final List<ChatConversation> conversations = parser.parseHtmlFile(htmlFile);
        assertThat(conversations).isNotEmpty();

        log.info("步骤 1：全量 HTML 解析完成 | 原始 Conversation 话题总数: {} 个", conversations.size());

        final List<ChatSession> sessions = vectorSplitter.splitConversations(conversations);
        assertThat(sessions).isNotEmpty();

        log.info("步骤 2：BGE 向量语义切分完成 | 切分后 Session 文档单元总数: {} 个", sessions.size());
        log.info("------------------------------------------------------------------------------------------");
        log.info("                                 【切分后的样例 Session 文档展示】");
        log.info("------------------------------------------------------------------------------------------");

        final int sampleDisplayLimit = Math.min(5, sessions.size());
        for (int i = 0; i < sampleDisplayLimit; i++) {
            final ChatSession session = sessions.get(i);
            log.info("Session 文档 [{}] | ID: {} | 包含消息数: {} 条",
                    i + 1, session.getSessionId(), session.getMessages().size());
            log.info("  起止时间: {} ~ {}", session.getStartTime(), session.getEndTime());
            if (!session.getMessages().isEmpty()) {
                final String firstMsg = session.getMessages().get(0).getContent();
                final String lastMsg = session.getMessages().get(session.getMessages().size() - 1).getContent();
                log.info("  首条消息摘要: \"{}\"", firstMsg.length() > 60 ? firstMsg.substring(0, 60) + "..." : firstMsg);
                log.info("  末条消息摘要: \"{}\"", lastMsg.length() > 60 ? lastMsg.substring(0, 60) + "..." : lastMsg);
            }
            log.info("------------------------------------------------------------------------------------------");
        }

        log.info("==========================================================================================");
        log.info("全量文件向量语义切分验证完成，共拆分生成 {} 个独立语义 Session 文档！", sessions.size());
        log.info("==========================================================================================");
    }
}
