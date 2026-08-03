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
 * 中文：提取主线分支后的单场完整对话领域实体 POJO。
 * English: Extracted mainline single complete conversation domain entity POJO.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：包含了从原始 Mapping 树图提纯出的按时间顺序排列的单条主线消息列表。
 * English: Holds ordered mainline messages extracted from raw mapping DAG tree.
 * </p>
 *
 * 字段说明 / Field Description:
 * - conversationId: 对话全局 UUID
 * - title: 对话标题（侧边栏主题名称）
 * - createTime: 对话创建时间 (格式如：yyyy-MM-dd HH:mm:ss)
 * - messages: 主线采纳的有序消息列表
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatConversation", description = "提取主线后的单场完整对话领域实体")
public class ChatConversation {

    @Schema(description = "对话全局唯一标识 ID", example = "693d9713-bda8-8322-be78-98dad7965585")
    private String conversationId;

    @Schema(description = "对话标题（对应 ChatGPT 侧边栏主题名称）", example = "翻译内容总结")
    private String title;

    @Schema(description = "对话创建时间戳（格式：yyyy-MM-dd HH:mm:ss）", example = "2025-12-14 00:40:57")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Instant createTime;

    @Schema(description = "主线采纳的有序消息列表（严格按时间正序排列）")
    private List<ChatMessage> messages;
}
