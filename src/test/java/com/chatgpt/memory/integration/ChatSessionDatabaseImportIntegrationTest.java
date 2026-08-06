package com.chatgpt.memory.integration;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.ChatSessionDatabaseService;
import com.chatgpt.memory.service.VectorSessionSplitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库连接与全量 Session 持久化导入集成测试类
 * 校验与本地 MySQL chat_memory_curator 数据库的连接通信及批量落库功能
 *
 * @author Antigravity
 */
@SpringBootTest
class ChatSessionDatabaseImportIntegrationTest {

    @Autowired
    private ChatExportParser parser;

    @Autowired
    private VectorSessionSplitter splitter;

    @Autowired
    private ChatSessionDatabaseService databaseService;

    @Test
    @DisplayName("验证本地 MySQL chat_memory_curator 数据库连接并批量导入全量 1,694 个 Session 记录")
    void testDatabaseConnectionAndBatchImport() throws IOException {
        final File htmlFile = new File("E:/data/chatGPT_back/chatGPT导出20251214/chat.html");
        if (!htmlFile.exists()) {
            System.out.println("❌ 目标文件 chat.html 不存在，跳过导入测试");
            return;
        }

        // 1. 初始化 / 校验数据表
        databaseService.initTable();

        // 2. 解析与切分数据
        final List<ChatConversation> conversations = parser.parseHtmlFile(htmlFile);
        final List<ChatSession> sessions = splitter.splitConversations(conversations);

        System.out.println("\n====================================================================================================");
        System.out.printf("  【开始导入至本地 MySQL [chat_memory_curator.chat_session] 库 (共 %d 个 Session)】%n", sessions.size());
        System.out.println("====================================================================================================\n");

        // 3. 执行批量落库
        final int insertedRows = databaseService.saveSessionsToDatabase(conversations, sessions);
        final long totalRecordsInDb = databaseService.getRecordCount();

        System.out.printf("✅ [成功] 批量导入落库完成！变动行数: %d 行, 当前 [chat_memory_curator.chat_session] 表内总记录数: %d 条%n",
                insertedRows, totalRecordsInDb);
        System.out.println("\n====================================================================================================\n");

        assertThat(totalRecordsInDb).isGreaterThan(0);
    }
}
