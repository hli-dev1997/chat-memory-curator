package com.chatgpt.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * 提纯后的单条线性消息领域实体 POJO
 * <p>
 * 经过树分支提取与角色清洗后得到的标准化消息模型，
 * 严格遵循《阿里 Java 开发手册》POJO 规约。
 * </p>
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 消息全局唯一标识 UUID
     */
    private String messageId;

    /**
     * 发送方角色标识（如 user, assistant）
     */
    private String role;

    /**
     * 提纯后的纯文本正文内容
     */
    private String content;

    /**
     * 消息创建时间戳（JSR-310 Instant 标准）
     */
    private Instant createTime;
}
