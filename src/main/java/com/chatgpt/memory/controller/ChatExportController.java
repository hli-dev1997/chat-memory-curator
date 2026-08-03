package com.chatgpt.memory.controller;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.ChatImportFacadeService;
import com.chatgpt.memory.service.ChatSessionDatabaseService;
import com.chatgpt.memory.service.ChatSessionSplitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 类说明 / Class Description:
 * 中文：ChatGPT 导出数据解析、切分与直存 MySQL 数据库的 REST 控制器。
 * English: REST controller for parsing, splitting, and directly persisting ChatGPT data to MySQL.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：遵守《阿里巴巴 Java 开发手册（黄山版）》API 规约，提供 OpenAPI 3 / Knife4j Swagger 可视化调试接口。
 * 支持直接指定 HTML 文件一键无磁盘 IO 直存 MySQL 数据库。
 * English: OpenAPI 3 REST controller supporting direct end-to-end import to MySQL database.
 * </p>

 * @author Antigravity
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "ChatExportController", description = "ChatGPT 导出数据解析、逻辑切分与 MySQL 直接落库接口")
public class ChatExportController {

    private final ChatExportParser chatExportParser;
    private final ChatSessionSplitter chatSessionSplitter;
    private final ChatSessionDatabaseService chatSessionDatabaseService;
    private final ChatImportFacadeService chatImportFacadeService;

    /**
     * 解析并切分 HTML 文件（返回内存列表，不依赖文件导出）
     */
    @GetMapping("/parse-and-split")
    @Operation(summary = "解析 HTML 文件并进行逻辑 Session 切分", description = "流式解析指定的 ChatGPT 导出 HTML 文件，并按 4 小时阈值切分为 Session 片段列表")
    public ResponseEntity<Map<String, Object>> parseAndSplit(
            @Parameter(description = "导出 HTML 文件绝对路径", example = "E:\\data\\chatGPT_back\\chatGPT导出20251214\\chat.html")
            @RequestParam final String filePath) {

        final File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "文件不存在: " + filePath,
                    "data", Map.of()
            ));
        }

        try {
            final List<ChatConversation> conversations = chatExportParser.parseHtmlFile(file);
            final List<ChatSession> sessions = chatSessionSplitter.splitConversations(conversations);

            final Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "解析与切分成功");
            result.put("conversationCount", conversations.size());
            result.put("sessionCount", sessions.size());

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Failed to parse HTML file: {}", filePath, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "解析失败: " + e.getMessage(),
                    "data", Map.of()
            ));
        }
    }

    /**
     * 一键端到端解析并直存 MySQL 数据库（无需磁盘 JSON 中转）
     */
    @PostMapping("/import-to-db")
    @Operation(summary = "一键直存 MySQL 数据库", description = "端到端解析指定 HTML 文件，进行 4 小时切分，并直接批量插入 MySQL 数据库（无需中间 JSON 文件）")
    public ResponseEntity<Map<String, Object>> importToDatabase(
            @Parameter(description = "导出 HTML 文件绝对路径", example = "E:\\data\\chatGPT_back\\chatGPT导出20251214\\chat.html")
            @RequestParam final String filePath,
            @Parameter(description = "用户 ID / 工号", example = "EMP001")
            @RequestParam(required = false, defaultValue = "EMP001") final String userId,
            @Parameter(description = "数据源头 AI 厂商 (CHATGPT, GEMINI, CLAUDE 等)", example = "CHATGPT")
            @RequestParam(required = false, defaultValue = "CHATGPT") final String source,
            @Parameter(description = "终端平台 (WEB, IDE, APP 等)", example = "WEB")
            @RequestParam(required = false, defaultValue = "WEB") final String platform) {

        final File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "文件不存在: " + filePath,
                    "data", Map.of()
            ));
        }

        try {
            final int insertedCount = chatImportFacadeService.importHtmlToDatabase(file, userId, source, platform);

            final Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "一键直存 MySQL 成功");
            result.put("insertedSessionCount", insertedCount);
            result.put("totalDatabaseRecords", chatSessionDatabaseService.getRecordCount());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Direct MySQL import failed for file: {}", filePath, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "落库失败: " + e.getMessage(),
                    "data", Map.of()
            ));
        }
    }
}
