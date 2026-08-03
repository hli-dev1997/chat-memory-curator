package com.chatgpt.memory.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：对应 RawNodeDto 节点上挂载的 ChatGPT 原始消息实体。
 * English: Raw message entity attached to a RawNodeDto node.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：用于提取发送者 Author 角色信息、Content 正文片段列表 parts，以及秒级创建时间戳 create_time。
 * English: Used to extract author role, content parts (strings/audio dicts), and creation timestamps.
 * </p>
 *
 * 字段说明 / Field Description:
 * - id: 消息 UUID
 * - author: 消息发送者对象
 * - content: 消息内容对象（包含 parts 列表）
 * - createTime: 消息发送的时间戳（秒）
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "RawMessageDto", description = "ChatGPT 原始消息节点 DTO 实体")
public class RawMessageDto {

    @Schema(description = "消息全局唯一标识 UUID", example = "b5532e3d-31e5-4217-bf08-4e26d86e3e6d")
    private String id;

    @Schema(description = "消息发送者元数据")
    private AuthorDto author;

    @Schema(description = "消息正文内容容器")
    private ContentDto content;

    @Schema(description = "消息创建时间戳（单位：秒）", example = "1765644057.564")
    @JsonProperty("create_time")
    private Double createTime;

    /**
     * 消息发送者属性实体
     */
    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "AuthorDto", description = "消息发送者角色实体")
    public static class AuthorDto {

        @Schema(description = "发送者角色类型（如 user, assistant, system, tool）", example = "user")
        private String role;
    }

    /**
     * 消息正文属性容器
     */
    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ContentDto", description = "消息正文 Parts 容器实体")
    public static class ContentDto {

        @Schema(description = "消息类型标识", example = "text")
        @JsonProperty("content_type")
        private String contentType;

        @Schema(description = "多模态与纯文本 parts 列表（包含纯字符串或带有 text 字段的语音转写字典对象）")
        private List<Object> parts;
    }
}
