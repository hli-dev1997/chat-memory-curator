package com.chatgpt.memory.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * 类说明 / Class Description:
 * 中文：对应 ChatGPT 导出数据 jsonData 数组中的单个 Conversation 原始 DTO 实体。
 * English: Raw DTO entity corresponding to a single conversation inside jsonData array exported from ChatGPT.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：用于 Jackson 反序列化 chat.html 中的 Raw 数据，提取包含 current_node 的消息树字典映射。
 * English: Used by Jackson to deserialize raw data in chat.html, extracting mapping dict with current_node.
 * </p>
 *
 * 字段说明 / Field Description:
 * - id: 对话唯一标识 ID
 * - title: 对话标题
 * - createTime: 秒级 Unix 时间戳
 * - updateTime: 秒级 Unix 更新时间戳
 * - currentNode: 活跃叶子节点指针 ID
 * - mapping: 节点 ID 拓扑字典
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "RawConversationDto", description = "ChatGPT 原始对话 JSON DTO 实体")
public class RawConversationDto {

    @Schema(description = "原始对话全局唯一标识 ID", example = "693d9713-bda8-8322-be78-98dad7965585")
    private String id;

    @Schema(description = "原始对话标题（侧边栏主题）", example = "翻译内容总结")
    private String title;

    @Schema(description = "对话创建时间戳（单位：秒）", example = "1765644057.454933")
    @JsonProperty("create_time")
    private Double createTime;

    @Schema(description = "对话最后更新时间戳（单位：秒）", example = "1765644120.779")
    @JsonProperty("update_time")
    private Double updateTime;

    @Schema(description = "当前选中的活跃叶子节点 ID，用于逆向回溯提取主线采纳分支", example = "0eb9cb28-ad40-4a5c-b348-f23458f26d66")
    @JsonProperty("current_node")
    private String currentNode;

    @Schema(description = "节点 ID 映射到 RawNodeDto 对象的拓扑树字典")
    private Map<String, RawNodeDto> mapping;
}
