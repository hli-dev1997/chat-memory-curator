package com.chatgpt.memory.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * 类说明 / Class Description:
 * 中文：提纯后的单条线性消息领域实体 POJO。
 * English: Cleaned single linear message domain entity POJO.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：经过树分支提取与角色清洗后得到的标准化消息模型，严格遵循《阿里 Java 开发手册》POJO 规约。
 * English: Standardized message model after tree branch extraction and role washing, adhering to Alibaba Java manual.
 * </p>
 *
 * 字段说明 / Field Description:
 * - messageId: 消息全局唯一 ID
 * - role: 发送方角色 (user / assistant)
 * - content: 提纯后的纯文本正文内容
 * - createTime: 消息创建时间戳 (格式如：yyyy-MM-dd HH:mm:ss)
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatMessage", description = "标准化提纯后的单条消息领域实体")
public class ChatMessage {

    @Schema(description = "消息全局唯一标识 UUID", example = "b5532e3d-31e5-4217-bf08-4e26d86e3e6d")
    private String messageId;

    @Schema(description = "发送方角色标识（如 user 代表用户，assistant 代表 AI 助手）", example = "user")
    private String role;

    @Schema(description = "提纯后的纯文本正文内容（已剔除图片指针与系统提示词噪音）", example = "翻译一下")
    private String content;

    @Schema(description = "消息创建时间戳（格式：yyyy-MM-dd HH:mm:ss）", example = "2025-12-14 00:40:57")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Instant createTime;
}
