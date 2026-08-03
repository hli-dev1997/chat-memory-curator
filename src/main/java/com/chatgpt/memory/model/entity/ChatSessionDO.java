package com.chatgpt.memory.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 类说明 / Class Description:
 * 中文：chat_session 数据库表持久化实体 (Data Object)。
 * English: Data Object (DO) entity corresponding to chat_session MySQL database table.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：严格遵循《阿里巴巴 Java 开发手册（黄山版）》数据库规约，存储切分后的 Session 实体及 JSON 文本。
 * 包含用户 ID userId、数据源 source (CHATGPT, GEMINI) 与终端平台 platform (WEB, IDE)。
 * English: Store split Session entities and JSON text into MySQL complying with Alibaba Java Manual.
 * </p>
 *
 * 字段说明 / Field Description:
 * - id: 自增主键 ID
 * - userId: 用户 ID / 工号
 * - source: 数据源头 AI 模型/厂商 (CHATGPT, GEMINI, CLAUDE 等)
 * - platform: 终端/客户端平台 (WEB, IDE, APP 等)
 * - sessionId: 片段唯一标识
 * - parentConversationId: 关联父对话 ID
 * - title: 对话标题
 * - messageCount: 消息总条数
 * - startTime: 片段起始时间
 * - endTime: 片段结束时间
 * - sessionJson: 完整 JSON 文本
 * - createTime: 记录创建时间
 * - updateTime: 记录更新时间
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatSessionDO", description = "chat_session 数据库表持久化实体")
public class ChatSessionDO {

    @Schema(description = "自增主键 ID", example = "1")
    private Long id;

    @Schema(description = "用户 ID / 工号", example = "EMP001")
    private String userId;

    @Schema(description = "数据源头 AI 模型/厂商（如 CHATGPT, GEMINI, CLAUDE, KIMI）", example = "CHATGPT")
    private String source;

    @Schema(description = "终端/客户端平台（如 WEB 代表网页端，IDE 代表编辑器插件，APP 代表移动端）", example = "WEB")
    private String platform;

    @Schema(description = "会话片段唯一标识 (如 convId-s1)", example = "693d9713-bda8-8322-be78-98dad7965585-s1")
    private String sessionId;

    @Schema(description = "所属原始对话 ID (关联父框框)", example = "693d9713-bda8-8322-be78-98dad7965585")
    private String parentConversationId;

    @Schema(description = "对话标题", example = "翻译内容总结")
    private String title;

    @Schema(description = "本片段包含的消息总条数", example = "4")
    private Integer messageCount;

    @Schema(description = "本片段起始消息发送时间")
    private LocalDateTime startTime;

    @Schema(description = "本片段结束消息发送时间")
    private LocalDateTime endTime;

    @Schema(description = "全量 JSON 文本 (存储本片段所有的 ChatMessage 列表)")
    private String sessionJson;

    @Schema(description = "记录创建时间 (阿里规约必备)")
    private LocalDateTime createTime;

    @Schema(description = "记录更新时间 (阿里规约必备)")
    private LocalDateTime updateTime;
}
