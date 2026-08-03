package com.chatgpt.memory.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 对应 ChatGPT 导出数据 mapping 字典中的单节点 DTO
 * <p>
 * ChatGPT 对话是以树图（DAG）结构存储的，每个节点包含指向父节点的 parent 指针
 * 以及指向子节点列表的 children 指针，用于支持重新生成回答与编辑消息产生的分支。
 * </p>
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawNodeDto {

    /**
     * 节点全局唯一标识 ID
     */
    private String id;

    /**
     * 父节点指针 ID（根节点 parent 为 null 或 client-created-root）
     */
    private String parent;

    /**
     * 子节点指针 ID 集合
     */
    private List<String> children;

    /**
     * 节点挂载的原始消息实体（根节点或部分过渡节点的 message 可能为 null）
     */
    private RawMessageDto message;
}
