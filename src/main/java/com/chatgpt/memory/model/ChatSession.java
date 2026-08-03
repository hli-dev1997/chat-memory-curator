package com.chatgpt.memory.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：经过时间间隔（默认 4 小时阈值）切分后的逻辑会话片段领域实体 POJO。
 * English: Logical chat session fragment POJO split by time interval (default 4h threshold).
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：单场 ChatConversation 可能跨越数天或数个不同话题，
 * 经过 ChatSessionSplitter 按时间间隔切分为多个相对聚焦的 ChatSession，
 * 作为 Phase 2 AI 价值评估与记忆库向量化的最小逻辑处理单元。
 * 支持包含用户 ID userId、数据源 source (CHATGPT, GEMINI) 与终端平台 platform (WEB, IDE)。
 * English: Split long conversations into focused session fragments as minimum units for Phase 2 AI evaluation & vectorization.
 * </p>
 *
 * 字段说明 / Field Description:
 * - sessionId: 会话片段唯一 ID (convId-s1, convId-s2...)
 * - parentConversationId: 归属的原始 Conversation ID
 * - userId: 用户 ID / 工号
 * - source: 数据源头 AI 模型/厂商 (CHATGPT, GEMINI, CLAUDE 等)
 * - platform: 终端/客户端平台 (WEB-网页端, IDE-编辑器插件 等)
 * - startTime: 片段起始时间 (格式如：yyyy-MM-dd HH:mm:ss)
 * - endTime: 片段结束时间 (格式如：yyyy-MM-dd HH:mm:ss)
 * - messages: 片段内包含的有序消息列表
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatSession", description = "按时间间隔切分后的逻辑 Session 片段实体")
public class ChatSession {

    @Schema(description = "会话片段唯一标识 ID（格式：convId-s1, convId-s2）", example = "693d9713-bda8-8322-be78-98dad7965585-s1")
    private String sessionId;

    @Schema(description = "所属原始 Conversation 的全局唯一标识 ID，用于关联父对话", example = "693d9713-bda8-8322-be78-98dad7965585")
    private String parentConversationId;

    @Schema(description = "用户 ID / 工号", example = "EMP001")
    private String userId;

    @Schema(description = "数据源头 AI 模型/厂商（如 CHATGPT, GEMINI, CLAUDE, KIMI）", example = "CHATGPT")
    private String source;

    @Schema(description = "终端/客户端平台（如 WEB 代表网页端，IDE 代表编辑器插件，APP 代表移动端）", example = "WEB")
    private String platform;

    @Schema(description = "该会话片段起始消息的创建时间戳（格式：yyyy-MM-dd HH:mm:ss）", example = "2025-12-14 00:40:57")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Instant startTime;

    @Schema(description = "该会话片段结束消息的创建时间戳（格式：yyyy-MM-dd HH:mm:ss）", example = "2025-12-14 00:41:20")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Instant endTime;

    @Schema(description = "该会话片段内包含的有序消息列表")
    private List<ChatMessage> messages;
}
