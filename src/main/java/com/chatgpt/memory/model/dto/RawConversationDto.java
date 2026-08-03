package com.chatgpt.memory.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * 对应 ChatGPT 导出数据 jsonData 数组中的单个 Conversation 原始 DTO
 * <p>
 * 包含了整场对话的元数据、当前活跃的叶子节点 current_node，
 * 以及包含了整棵消息树节点的 mapping 字典映射。
 * </p>
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawConversationDto {

    /**
     * 原始对话唯一标识 ID
     */
    private String id;

    /**
     * 原始对话标题
     */
    private String title;

    /**
     * 对话创建时间戳（单位：秒）
     */
    @JsonProperty("create_time")
    private Double createTime;

    /**
     * 对话最后更新时间戳（单位：秒）
     */
    @JsonProperty("update_time")
    private Double updateTime;

    /**
     * 当前选中的活跃叶子节点 ID，用于逆向回溯提取主线采纳分支
     */
    @JsonProperty("current_node")
    private String currentNode;

    /**
     * 节点 ID -> 节点对象的拓扑字典映射
     */
    private Map<String, RawNodeDto> mapping;
}
