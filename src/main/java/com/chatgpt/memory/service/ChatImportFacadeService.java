package com.chatgpt.memory.service;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：ChatGPT 数据一键全链路解析、切分并直存 MySQL 数据库的门面服务 (Facade Service)。
 * English: Facade service for parsing, splitting, and directly persisting ChatGPT data to MySQL in one shot.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：无需磁盘 JSON 文件中转，直接在内存中完成 87.6MB 数据提纯、4 小时 Session 切分，并直接批量插入 MySQL。
 * English: Bypass disk JSON files, performing in-memory parsing & splitting, and directly inserting into MySQL.
 * </p>

 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatImportFacadeService {

    private final ChatExportParser chatExportParser;
    private final ChatSessionSplitter chatSessionSplitter;
    private final ChatSessionDatabaseService chatSessionDatabaseService;

    /**
     * 从 HTML 文件直接解析、切分并一键落库至 MySQL 数据库
     *
     * @param htmlFile HTML 文件对象
     * @param userId   用户 ID / 工号
     * @param source   数据源头 AI 厂商 (如 CHATGPT, GEMINI, CLAUDE)
     * @param platform 终端平台 (如 WEB, IDE, APP)
     * @return 成功落库的 Session 记录数
     * @throws IOException 文件读取异常
     */
    public int importHtmlToDatabase(
            final File htmlFile,
            final String userId,
            final String source,
            final String platform) throws IOException {

        log.info("Starting direct end-to-end import for file: {}, userId: {}, source: {}, platform: {}",
                htmlFile.getName(), userId, source, platform);

        // 步骤 1：流式快速解析提纯主线对话
        final long startParse = System.currentTimeMillis();
        final List<ChatConversation> conversations = chatExportParser.parseHtmlFile(htmlFile);
        final long parseCost = System.currentTimeMillis() - startParse;

        // 步骤 2：4 小时无交互时间间隔逻辑切分
        final long startSplit = System.currentTimeMillis();
        final List<ChatSession> sessions = chatSessionSplitter.splitConversations(conversations);
        final long splitCost = System.currentTimeMillis() - startSplit;

        // 步骤 3：内存数据直接批量 Upsert 落库 MySQL
        final long startDb = System.currentTimeMillis();
        final int insertedRows = chatSessionDatabaseService.saveSessionsToDatabase(
                conversations, sessions, userId, source, platform);
        final long dbCost = System.currentTimeMillis() - startDb;

        log.info("Direct import completed! Parse: {} ms, Split: {} ms, DB Upsert: {} ms. Total {} sessions saved.",
                parseCost, splitCost, dbCost, insertedRows);

        return insertedRows;
    }
}
