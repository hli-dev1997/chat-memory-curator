package com.chatgpt.memory.service;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.ChatSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于时间间隔对对话主线消息进行逻辑切分的服务实现
 * <p>
 * 单场 ChatConversation 可能包含跨越数天或数个话题的讨论，
 * 本服务依据设定阈值（默认 4 小时），将逻辑中断的对话分割为相对聚焦的 ChatSession，
 * 为 Phase 2 的记忆库评估与向量化构建提供独立处理单元。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Component
public class ChatSessionSplitter {

    /**
     * 会话切分的时间间隔阈值（单位：小时，通过配置文件动态注入，默认 4 小时）
     */
    private final long splitIntervalHours;

    /**
     * 构造方法：从 Spring 容器注入配置切分间隔
     *
     * @param splitIntervalHours 时间间隔阈值 (小时)
     */
    public ChatSessionSplitter(
            @Value("${chat.session.split-interval-hours:4}") final long splitIntervalHours) {
        this.splitIntervalHours = splitIntervalHours;
    }

    /**
     * 批量处理对话集合，切分为 Session 列表
     *
     * @param conversations 对话列表
     * @return 切分后的 Session 列表
     */
    public List<ChatSession> splitConversations(final List<ChatConversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyList();
        }

        final List<ChatSession> allSessions = new ArrayList<>();
        for (final ChatConversation conv : conversations) {
            allSessions.addAll(splitConversation(conv));
        }

        log.info("Split {} conversations into {} total sessions (threshold: {} hours)",
                conversations.size(), allSessions.size(), splitIntervalHours);
        return allSessions;
    }

    /**
     * 对单个 Conversation 按消息创建时间间隔进行逻辑切分
     *
     * @param conversation 对话实体
     * @return 切分得到的 Session 列表
     */
    public List<ChatSession> splitConversation(final ChatConversation conversation) {
        if (conversation == null || conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            return Collections.emptyList();
        }

        final List<ChatMessage> messages = conversation.getMessages();
        final String convId = conversation.getConversationId();
        final List<ChatSession> sessions = new ArrayList<>();

        List<ChatMessage> currentSessionMessages = new ArrayList<>();
        Instant sessionStartTime = null;
        Instant previousMsgTime = null;
        int sessionIndex = 1;

        for (final ChatMessage message : messages) {
            // 防御性校验
            if (message == null) {
                continue;
            }

            // 安全获取消息时间戳
            final Instant msgTime = message.getCreateTime() != null
                    ? message.getCreateTime()
                    : (previousMsgTime != null ? previousMsgTime : Instant.now());

            if (currentSessionMessages.isEmpty()) {
                // 当前片段的第一条消息，初始化片段属性
                currentSessionMessages.add(message);
                sessionStartTime = msgTime;
                previousMsgTime = msgTime;
            } else {
                // 计算与上一条消息的时间差
                final Duration gap = Duration.between(previousMsgTime, msgTime);

                // 核心判定：时间间隔绝对值超过设定阈值（如 >= 4小时），触发 Session 切分
                if (Math.abs(gap.toHours()) >= splitIntervalHours) {
                    // 归档当前 Session
                    final ChatSession session = createSessionObject(
                            convId,
                            sessionIndex++,
                            sessionStartTime,
                            previousMsgTime,
                            currentSessionMessages
                    );
                    sessions.add(session);

                    // 开启新的 Session 片段
                    currentSessionMessages = new ArrayList<>();
                    currentSessionMessages.add(message);
                    sessionStartTime = msgTime;
                } else {
                    // 未超阈值，归入当前 Session 片段
                    currentSessionMessages.add(message);
                }
                previousMsgTime = msgTime;
            }
        }

        // 刷新收尾最后一个 Session 片段
        if (!currentSessionMessages.isEmpty()) {
            final ChatSession lastSession = createSessionObject(
                    convId,
                    sessionIndex,
                    sessionStartTime,
                    previousMsgTime,
                    currentSessionMessages
            );
            sessions.add(lastSession);
        }

        return sessions;
    }

    /**
     * 辅助工厂方法：构建 ChatSession 片段实体
     *
     * @param convId    原始 Conversation ID
     * @param index     片段序号
     * @param startTime 片段起始时间
     * @param endTime   片段结束时间
     * @param messages  消息列表
     * @return 构造完成的 ChatSession 对象
     */
    private ChatSession createSessionObject(
            final String convId,
            final int index,
            final Instant startTime,
            final Instant endTime,
            final List<ChatMessage> messages) {

        final String sessionId = Objects.requireNonNullElse(convId, "conv") + "-s" + index;
        final ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setParentConversationId(convId);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setMessages(messages);
        return session;
    }
}
