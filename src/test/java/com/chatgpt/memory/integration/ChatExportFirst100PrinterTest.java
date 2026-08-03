package com.chatgpt.memory.integration;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.parser.ChatExportParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 提纯提取前 100 场完整 Conversation（非切分 Session）的打印与导出测试类
 *
 * @author Antigravity
 */
@Slf4j
@SpringBootTest
class ChatExportFirst100PrinterTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Autowired
    private ChatExportParser parser;

    @Test
    @DisplayName("提取前 100 场完整对话，控制台展示并同步导出为 extracted_first_100_conversations.json")
    void printAndExportFirst100Conversations() throws IOException {
        final File htmlFile = new File("E:/data/chatGPT_back/chatGPT导出20251214/chat.html");
        if (!htmlFile.exists()) {
            log.warn("chat.html 文件不存在，跳过打印");
            return;
        }

        // 1. 解析提纯整份文件中的所有对话
        final List<ChatConversation> allConversations = parser.parseHtmlFile(htmlFile);

        // 2. 截取前 100 场完整对话（非片段切分）
        final int limit = Math.min(100, allConversations.size());
        final List<ChatConversation> first100 = allConversations.subList(0, limit);

        log.info("====================================================================================================");
        log.info("               【前 {} 场完整对话 (ChatConversation) 提纯明细】", limit);
        log.info("====================================================================================================");

        for (int i = 0; i < first100.size(); i++) {
            final ChatConversation conv = first100.get(i);
            log.info("【对话 {} / {}】--------------------------------------------------------------------------------",
                    String.format("%03d", i + 1), String.format("%03d", limit));
            log.info("  - 对话 ID   : {}", conv.getConversationId());
            log.info("  - 对话标题 : {}", conv.getTitle());
            log.info("  - 创建时间 : {}", DATE_FORMATTER.format(conv.getCreateTime()));
            log.info("  - 消息条数 : {} 条", conv.getMessages().size());
            log.info("  --------------------------------------------------------------------------------------------------");

            final List<ChatMessage> msgs = conv.getMessages();
            for (int j = 0; j < msgs.size(); j++) {
                final ChatMessage msg = msgs.get(j);
                final String timeStr = DATE_FORMATTER.format(msg.getCreateTime());
                final String roleTag = "user".equalsIgnoreCase(msg.getRole()) ? "👤 User" : "🤖 Assistant";

                // 单条消息正文格式化预览
                String cleanContent = msg.getContent().replace("\n", " ").trim();
                if (cleanContent.length() > 150) {
                    cleanContent = cleanContent.substring(0, 150) + "...";
                }

                log.info("    [{}] {} ({}):", String.format("%02d", j + 1), roleTag, timeStr);
                log.info("         \"{}\"", cleanContent);
            }
            log.info("----------------------------------------------------------------------------------------------------");
        }

        // 3. 将这 100 场完整的结构化对话导出为格式化 JSON 文件供人工查验
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        final File outputFile = new File("E:/data/chatgpt-memory-exporter/extracted_first_100_conversations.json");
        mapper.writeValue(outputFile, first100);

        log.info(">>> [成功] 前 {} 场完整对话已成功导出至 JSON 文件: {}", limit, outputFile.getAbsolutePath());
    }
}

