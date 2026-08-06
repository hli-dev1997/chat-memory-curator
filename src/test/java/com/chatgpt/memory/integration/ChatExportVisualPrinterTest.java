package com.chatgpt.memory.integration;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.VectorSessionSplitter;
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
 * 完整对话内容提取与切分的可视化展示测试类
 * 用于直观展示从 chat.html 中提纯出的单场完整对话以及 BGE 向量切分后的 Session 格式
 *
 * @author Antigravity
 */
@Slf4j
@SpringBootTest
class ChatExportVisualPrinterTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Autowired
    private ChatExportParser parser;

    @Autowired
    private VectorSessionSplitter splitter;

    @Test
    @DisplayName("打印提纯后的完整对话及按时间切分的 Session 片段")
    void printExtractedConversationDetails() throws IOException {
        final File htmlFile = new File("E:/data/chatGPT_back/chatGPT导出20251214/chat.html");
        if (!htmlFile.exists()) {
            log.warn("chat.html 文件不存在，跳过打印");
            return;
        }

        // 1. 解析提纯整份文件
        final List<ChatConversation> conversations = parser.parseHtmlFile(htmlFile);

        log.info("====================================================================================================");
        log.info("                        【ChatGPT 导出数据提纯与切分可视化样例展示】");
        log.info("====================================================================================================");

        // 挑选 2 场典型的长对话展示完整的提纯与切分内容（如 Redis集群架构类型 与 虚拟机健康检查）
        int printedCount = 0;
        for (final ChatConversation conv : conversations) {
            // 挑选消息条数 >= 6 且包含切分 Session 的丰富对话
            final List<ChatSession> sessions = splitter.splitConversation(conv);
            if (sessions.size() >= 2 && printedCount < 2) {
                printedCount++;
                printSingleConversationDetail(conv, sessions, printedCount);
            }
        }
    }

    private void printSingleConversationDetail(
            final ChatConversation conv,
            final List<ChatSession> sessions,
            final int index) {

        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>> 【完整对话样例 {}】 <<<<<<<<<<<<<<<<<<<<<<<<<<<<", index);
        log.info("【对话 ID】: {}", conv.getConversationId());
        log.info("【对话标题】: {}", conv.getTitle());
        log.info("【创建时间】: {}", DATE_FORMATTER.format(conv.getCreateTime()));
        log.info("【主线提纯后消息总数】: {} 条", conv.getMessages().size());
        log.info("【4小时切分得到的 Session 数量】: {} 个片段", sessions.size());
        log.info("----------------------------------------------------------------------------------------------------");

        for (int i = 0; i < sessions.size(); i++) {
            final ChatSession session = sessions.get(i);
            log.info("  --- [Session 片段 {} / {}] (ID: {}) ---", i + 1, sessions.size(), session.getSessionId());
            log.info("  --- [时间跨度]: {}  ==>  {} (包含 {} 条消息) ---",
                    DATE_FORMATTER.format(session.getStartTime()),
                    DATE_FORMATTER.format(session.getEndTime()),
                    session.getMessages().size());
            log.info("  --------------------------------------------------------------------------------------------------");

            final List<ChatMessage> msgs = session.getMessages();
            for (int j = 0; j < msgs.size(); j++) {
                final ChatMessage msg = msgs.get(j);
                final String timeStr = DATE_FORMATTER.format(msg.getCreateTime());
                final String roleName = "user".equalsIgnoreCase(msg.getRole()) ? "👤 用户 (User)" : "🤖 AI 助手 (Assistant)";
                
                // 截取预览正文（前 120 个字符）
                String contentPreview = msg.getContent().replace("\n", " ").trim();
                if (contentPreview.length() > 120) {
                    contentPreview = contentPreview.substring(0, 120) + "...";
                }

                log.info("    [{}.{}] {} ({}):", i + 1, j + 1, roleName, timeStr);
                log.info("          \"{}\"", contentPreview);
            }
            log.info("  --------------------------------------------------------------------------------------------------");
        }
        log.info("====================================================================================================");
    }
}

