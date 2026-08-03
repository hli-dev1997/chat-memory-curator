package com.chatgpt.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

/**
 * 经过时间间隔（默认 4 小时阈值）切分后的逻辑会话片段领域实体 POJO
 * <p>
 * 单场 ChatConversation 可能跨越数天或数个不同话题，
 * 经过 ChatSessionSplitter 按时间间隔切分为多个相对聚焦的 ChatSession，
 * 作为 Phase 2 AI 价值评估与记忆库向量化的最小逻辑处理单元。
 * </p>
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    /**
     * 会话片段唯一标识（格式如：convId-s1, convId-s2）
     */
    private String sessionId;

    /**
     * 所属原始 Conversation 的全局唯一标识 ID
     */
    private String parentConversationId;

    /**
     * 该会话片段起始消息的创建时间戳
     */
    private Instant startTime;

    /**
     * 该会话片段结束消息的创建时间戳
     */
    private Instant endTime;

    /**
     * 该会话片段内包含的有序消息列表
     */
    private List<ChatMessage> messages;
}
