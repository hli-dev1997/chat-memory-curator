package com.chatgpt.memory.integration;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.ChatSessionSplitter;
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
import java.util.List;

/**
 * 全量数据提取与会话片段切分全流程运行测试类
 * <p>
 * 运行本测试类可对 e:/data/chatGPT_back/chatGPT导出20251214/chat.html (87.6MB)
 * 执行全量数据提纯与 4 小时 Session 切分，并将结果分别导出为：
 * 1. all_extracted_conversations.json (1,142 场完整主线对话)
 * 2. all_split_sessions.json (1,694 个切分后的逻辑 Session 片段)
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@SpringBootTest
class ChatExportFullProcessRunnerTest {

    @Autowired
    private ChatExportParser parser;

    @Autowired
    private ChatSessionSplitter splitter;

    @Test
    @DisplayName("运行全量数据提取与 4 小时 Session 切分，导出全量结果文件")
    void executeFullExportAndSplitting() throws IOException {
        final File htmlFile = new File("E:/data/chatGPT_back/chatGPT导出20251214/chat.html");
        if (!htmlFile.exists()) {
            log.warn("❌ 目标文件 chat.html 不存在，请检查路径: {}", htmlFile.getAbsolutePath());
            return;
        }

        log.info("====================================================================================================");
        log.info("                     【开始执行 ChatGPT 87.6MB 全量数据提纯与 Session 切分】");
        log.info("====================================================================================================");

        // 1. 全量解析提纯主线对话
        final long startParseTime = System.currentTimeMillis();
        final List<ChatConversation> conversations = parser.parseHtmlFile(htmlFile);
        final long parseCostMs = System.currentTimeMillis() - startParseTime;

        log.info("✅ [1/2] 全量 HTML 解析完成！耗时: {} ms, 提取到主线对话场数: {} 场", parseCostMs, conversations.size());

        // 2. 全量按时间间隔（默认 4 小时）执行逻辑切分
        final long startSplitTime = System.currentTimeMillis();
        final List<ChatSession> sessions = splitter.splitConversations(conversations);
        final long splitCostMs = System.currentTimeMillis() - startSplitTime;

        log.info("✅ [2/2] 4小时时间间隔切分完成！耗时: {} ms, 产生的逻辑 Session 片段数: {} 个", splitCostMs, sessions.size());

        // 3. 结果序列化与文件导出
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // (1) 导出 1,142 场完整对话
        final File convOutputFile = new File("E:/data/chatgpt-memory-exporter/all_extracted_conversations.json");
        mapper.writeValue(convOutputFile, conversations);
        log.info("📦 [文件 1] 全量主线对话 (1,142 场) 已成功导出至: {} (文件大小: {} MB)",
                convOutputFile.getAbsolutePath(), String.format("%.2f", convOutputFile.length() / (1024.0 * 1024.0)));

        // (2) 导出 1,694 个 4小时切分 Session 片段
        final File sessionOutputFile = new File("E:/data/chatgpt-memory-exporter/all_split_sessions.json");
        mapper.writeValue(sessionOutputFile, sessions);
        log.info("📦 [文件 2] 全量切分 Session (1,694 个) 已成功导出至: {} (文件大小: {} MB)",
                sessionOutputFile.getAbsolutePath(), String.format("%.2f", sessionOutputFile.length() / (1024.0 * 1024.0)));

        log.info("====================================================================================================");
        log.info("                          🎉 【全量数据处理完成！准备好进入 Phase 2】");
        log.info("====================================================================================================");
    }
}

