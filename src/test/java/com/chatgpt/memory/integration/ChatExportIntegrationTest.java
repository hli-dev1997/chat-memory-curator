package com.chatgpt.memory.integration;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.ChatSessionSplitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatExportIntegrationTest {

    @Autowired
    private ChatExportParser parser;

    @Autowired
    private ChatSessionSplitter splitter;

    @Test
    @DisplayName("真实环境集成测试：高效解析 87MB chat.html 并验证切分结果")
    void testRealChatHtmlParsingAndSplitting() throws IOException {
        final File realHtmlFile = new File("E:/data/chatGPT_back/chatGPT导出20251214/chat.html");
        if (!realHtmlFile.exists()) {
            return;
        }

        final long startMs = System.currentTimeMillis();
        final List<ChatConversation> conversations = parser.parseHtmlFile(realHtmlFile);
        final long parseCostMs = System.currentTimeMillis() - startMs;

        assertThat(conversations).isNotEmpty();
        System.out.printf(">>> [集成测试] 解析 87MB real html 成功！找到对话场数: %d, 耗时: %d ms%n",
                conversations.size(), parseCostMs);

        final List<ChatSession> sessions = splitter.splitConversations(conversations);
        System.out.printf(">>> [集成测试] 切分完成！产生逻辑 Session 数: %d%n", sessions.size());

        assertThat(sessions).isNotEmpty();
    }
}
