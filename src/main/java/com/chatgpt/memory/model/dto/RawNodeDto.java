package com.chatgpt.memory.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：对应 ChatGPT 导出数据 mapping 字典中的单节点 DTO 实体。
 * English: Single node DTO entity inside ChatGPT mapping dictionary.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：ChatGPT 对话是以树图（DAG）结构存储的，每个节点包含 parent 指针与 children 列表，用于支持多分支追溯。
 * English: ChatGPT conversations are stored in DAG tree structure; each node has parent pointer for mainline tracing.
 * </p>
 *
 * 字段说明 / Field Description:
 * - id: 节点全局 UUID
 * - parent: 父节点指针 ID
 * - children: 子节点 ID 列表
 * - message: 挂载的原始消息 DTO
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "RawNodeDto", description = "ChatGPT 树图拓扑节点 DTO 实体")
public class RawNodeDto {

    @Schema(description = "节点全局唯一标识 ID", example = "0eb9cb28-ad40-4a5c-b348-f23458f26d66")
    private String id;

    @Schema(description = "父节点指针 ID（根节点 parent 为 null 或 client-created-root）", example = "02f11e94-eda1-4ef1-a5fd-3897350d0bad")
    private String parent;

    @Schema(description = "子节点指针 ID 集合")
    private List<String> children;

    @Schema(description = "节点挂载的原始消息实体（根节点或部分过渡节点可能为 null）")
    private RawMessageDto message;
}
