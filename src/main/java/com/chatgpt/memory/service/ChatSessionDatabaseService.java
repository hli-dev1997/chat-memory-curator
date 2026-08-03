package com.chatgpt.memory.service;

import com.chatgpt.memory.mapper.ChatSessionMapper;
import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.model.entity.ChatSessionDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 类说明 / Class Description:
 * 中文：ChatSession 数据持久化至 MySQL 数据库的服务实现类。
 * English: Service implementation for persisting ChatSession data into MySQL database.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：支持全量导入 1,694 个 Session 片段至 chat_memory_curator 库的 chat_session 表中。
 * 支持配置注入：用户 ID (defaultUserId)、数据源 (defaultSource: CHATGPT) 与终端平台 (defaultPlatform: WEB)。
 * 支持旧表动态补全 user_id, source 和 platform 字段。
 * English: Support batch importing 1,694 session fragments into chat_session table in chat_memory_curator DB.
 * </p>

 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionDatabaseService {

    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    @Value("${chat.user-id:EMP001}")
    private String defaultUserId;

    @Value("${chat.source:CHATGPT}")
    private String defaultSource;

    @Value("${chat.platform:WEB}")
    private String defaultPlatform;

    /**
     * 初始化表结构与动态修补列类型
     */
    public void initTable() {
        try {
            chatSessionMapper.createTableIfNotExists();

            // 动态补全已存在旧表中的 user_id, source 和 platform 字段
            try {
                chatSessionMapper.addColumnUserIdIfNotExists();
            } catch (Exception ignored) {
            }
            try {
                chatSessionMapper.addColumnSourceIfNotExists();
            } catch (Exception ignored) {
            }
            try {
                chatSessionMapper.addColumnPlatformIfNotExists();
            } catch (Exception ignored) {
            }
            // 确保 session_json 使用 MySQL 原生 JSON 数据类型
            try {
                chatSessionMapper.modifyColumnSessionJsonToJson();
            } catch (Exception ignored) {
            }

            log.info("Database table [chat_session] in [chat_memory_curator] checked/initialized successfully (JSON column active)");
        } catch (Exception e) {
            log.error("Failed to initialize database table [chat_session]", e);
            throw new RuntimeException("Database table initialization failed", e);
        }
    }

    /**
     * 将全量 ChatSession 批量导入持久化至 MySQL chat_memory_curator 数据库
     *
     * @param conversations 原始对话列表（用于获取标题）
     * @param sessions      切分后的 Session 列表
     * @return 成功导入的记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveSessionsToDatabase(
            final List<ChatConversation> conversations,
            final List<ChatSession> sessions) {

        return saveSessionsToDatabase(conversations, sessions, defaultUserId, defaultSource, defaultPlatform);
    }

    /**
     * 带用户 ID、数据源与终端平台重载的批量导入持久化方法
     *
     * @param conversations 原始对话列表
     * @param sessions      Session 列表
     * @param userId        用户 ID / 工号
     * @param source        数据源头 AI 厂商 (CHATGPT, GEMINI, CLAUDE 等)
     * @param platform      终端平台 (WEB, IDE, APP 等)
     * @return 成功导入行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveSessionsToDatabase(
            final List<ChatConversation> conversations,
            final List<ChatSession> sessions,
            final String userId,
            final String source,
            final String platform) {

        if (sessions == null || sessions.isEmpty()) {
            log.warn("Sessions list is empty, nothing to import");
            return 0;
        }

        final String currentUserId = (userId != null && !userId.isBlank()) ? userId : defaultUserId;
        final String currentSource = (source != null && !source.isBlank()) ? source : defaultSource;
        final String currentPlatform = (platform != null && !platform.isBlank()) ? platform : defaultPlatform;

        // 建立 parentConversationId -> Title 的快速索引映射
        final Map<String, String> titleMap = conversations != null
                ? conversations.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ChatConversation::getConversationId,
                        c -> c.getTitle() != null ? c.getTitle() : "Untitled Conversation",
                        (v1, v2) -> v1))
                : Map.of();

        // 校验表是否存在并修补 JSON 字段类型
        initTable();

        final List<ChatSessionDO> dataObjects = new ArrayList<>(sessions.size());
        final ZoneId systemZone = ZoneId.systemDefault();

        for (final ChatSession session : sessions) {
            try {
                // 填充实体中的 userId, source 与 platform
                session.setUserId(currentUserId);
                session.setSource(currentSource);
                session.setPlatform(currentPlatform);

                final String jsonText = objectMapper.writeValueAsString(session.getMessages());
                final String title = titleMap.getOrDefault(session.getParentConversationId(), "Untitled Conversation");

                final LocalDateTime startTime = session.getStartTime() != null
                        ? LocalDateTime.ofInstant(session.getStartTime(), systemZone)
                        : LocalDateTime.now();

                final LocalDateTime endTime = session.getEndTime() != null
                        ? LocalDateTime.ofInstant(session.getEndTime(), systemZone)
                        : LocalDateTime.now();

                final ChatSessionDO doObject = ChatSessionDO.builder()
                        .userId(currentUserId)
                        .source(currentSource)
                        .platform(currentPlatform)
                        .sessionId(session.getSessionId())
                        .parentConversationId(session.getParentConversationId())
                        .title(title)
                        .messageCount(session.getMessages() != null ? session.getMessages().size() : 0)
                        .startTime(startTime)
                        .endTime(endTime)
                        .sessionJson(jsonText)
                        .build();

                dataObjects.add(doObject);
            } catch (Exception e) {
                log.error("Error serializing session [{}] to JSON", session.getSessionId(), e);
            }
        }

        // 分批批量插入（每批 200 条，规避 SQL 过大）
        final int batchSize = 200;
        int totalInserted = 0;

        for (int i = 0; i < dataObjects.size(); i += batchSize) {
            final int toIndex = Math.min(i + batchSize, dataObjects.size());
            final List<ChatSessionDO> batch = dataObjects.subList(i, toIndex);
            totalInserted += chatSessionMapper.batchUpsert(batch);
        }

        log.info("Successfully upserted {} session records for user [{}] source [{}] platform [{}] into MySQL [chat_memory_curator.chat_session]",
                totalInserted, currentUserId, currentSource, currentPlatform);
        return totalInserted;
    }

    /**
     * 获取表记录数
     */
    public long getRecordCount() {
        return chatSessionMapper.count();
    }
}
