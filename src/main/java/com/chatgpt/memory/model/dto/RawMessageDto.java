package com.chatgpt.memory.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * 对应 ChatGPT 导出 JSON 中 mapping 字典节点的原始消息 DTO
 * <p>
 * 包含消息 ID、作者角色、创建与更新时间戳、内容 parts 数组以及消息状态等信息。
 * 遵循《阿里 Java 开发手册》POJO 规范，禁止使用 is 前缀，统一使用包装数据类型。
 * </p>
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawMessageDto {

    /**
     * 消息全局唯一标识 UUID
     */
    private String id;

    /**
     * 消息作者与角色信息
     */
    private AuthorDto author;

    /**
     * 消息创建 Unix 时间戳（单位：秒）
     */
    @JsonProperty("create_time")
    private Double createTime;

    /**
     * 消息最后更新 Unix 时间戳（单位：秒）
     */
    @JsonProperty("update_time")
    private Double updateTime;

    /**
     * 消息正文与多模态内容封装对象
     */
    private ContentDto content;

    /**
     * 消息生成状态（如 finished_successfully）
     */
    private String status;

    /**
     * 消息作者详细属性 DTO
     */
    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthorDto {
        /**
         * 角色标识（如 user, assistant, system, tool）
         */
        private String role;

        /**
         * 作者名称（可为 null）
         */
        private String name;

        /**
         * 拓展元数据集合
         */
        private Map<String, Object> metadata;
    }

    /**
     * 消息内容封装 DTO
     */
    @Getter
    @Setter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentDto {
        /**
         * 内容类型标识（如 text, user_editable_context, multimodal_text 等）
         */
        @JsonProperty("content_type")
        private String contentType;

        /**
         * 正文片段列表，元素可能为 String 文本，也可能为 Map 字典（如语音转写 audio_transcription）
         */
        private List<Object> parts;
    }
}
