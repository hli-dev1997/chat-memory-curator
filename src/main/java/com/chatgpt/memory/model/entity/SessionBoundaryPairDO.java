package com.chatgpt.memory.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话切分消息边界决策过程表持久化实体 (Data Object)
 * <p>
 * 对应 session_boundary_pair 数据库表。
 * 存储相邻消息对（Pair）的 L1 向量初筛得分、区间分类、L2 千问精排裁决及最终决策，
 * 将"切分决策过程"与"切分最终产物（chat_session）"解耦，实现以下能力：
 * <ul>
 *   <li>L1 → L2 → Stage3 三阶段流水线的状态追踪</li>
 *   <li>断点续跑：只重扫 process_status = PENDING 的记录</li>
 *   <li>精准临界区人工抽查：通过 l1_zone + l1_score 范围查询定位高危 Pair</li>
 *   <li>人工复核队列：process_status = NEED_MANUAL_REVIEW 的记录集中管理</li>
 * </ul>
 * </p>
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SessionBoundaryPairDO", description = "会话切分消息边界决策过程表持久化实体")
public class SessionBoundaryPairDO {

    /**
     * 自增主键 ID
     */
    @Schema(description = "自增主键 ID", example = "1")
    private Long id;

    /**
     * 所属原始对话 ID（关联 chat_session.parent_conversation_id）
     */
    @Schema(description = "所属原始对话 ID", example = "693d2b44-ec88-8324-b330-427a95fc293a")
    private String parentConversationId;

    /**
     * 在当前 Conversation 中的 Pair 顺序序号（1-indexed）
     * 与 uk_conv_pair 唯一键配合，保证同一对话内 Pair 的顺序性
     */
    @Schema(description = "Pair 在当前对话中的顺序序号（1-indexed）", example = "1")
    private Integer pairIndex;

    /**
     * 前一条消息 ID 或内容哈希
     */
    @Schema(description = "前一条消息 ID 或内容哈希", example = "msg_001")
    private String messageAId;

    /**
     * 后一条消息 ID 或内容哈希
     */
    @Schema(description = "后一条消息 ID 或内容哈希", example = "msg_002")
    private String messageBId;

    /**
     * 前一条消息角色（user / assistant）
     */
    @Schema(description = "前一条消息角色", example = "user")
    private String messageARole;

    /**
     * 后一条消息角色（user / assistant）
     */
    @Schema(description = "后一条消息角色", example = "assistant")
    private String messageBRole;

    /**
     * 前一条消息文本摘要（存前 200 字，用于可视化诊断与人工核查展示）
     */
    @Schema(description = "前一条消息文本摘要（前 200 字）", example = "你好，请问关于 Redis Cluster...")
    private String messageAText;

    /**
     * 后一条消息文本摘要（存前 200 字，用于可视化诊断与人工核查展示）
     */
    @Schema(description = "后一条消息文本摘要（前 200 字）", example = "好的，Redis Cluster 的核心机制...")
    private String messageBText;

    /**
     * 是否包含图片或文件附件（1是/0否，阿里规约 unsigned tinyint 规范）
     */
    @Schema(description = "是否包含图片或文件附件（1是/0否）", example = "1")
    private Integer hasAttachment;

    /**
     * 消息 A 关联的全量附件与图片元数据 JSON 数组
     */
    @Schema(description = "消息 A 附件元数据 JSON", example = "[{\"type\":\"IMAGE\",\"url\":\"/chat-assets/file-xxx.png\"}]")
    private String attachmentMetaA;

    /**
     * 消息 B 关联的全量附件与图片元数据 JSON 数组
     */
    @Schema(description = "消息 B 附件元数据 JSON", example = "[{\"type\":\"DOCUMENT\",\"name\":\"dump.log\"}]")
    private String attachmentMetaB;

    /**
     * L1 BGE 向量余弦相似度得分（精确四位小数，禁止使用 float/double）
     */
    @Schema(description = "L1 BGE 向量余弦相似度得分", example = "0.7512")
    private BigDecimal l1Score;

    /**
     * L1 大区初判（GREEN_MERGE / FUZZY / RED_SPLIT）
     * 对应 L1ZoneEnum 的枚举编码
     */
    @Schema(description = "L1 大区初判", example = "FUZZY")
    private String l1Zone;

    /**
     * L2 千问大模型裁决（MERGE / SPLIT），仅 FUZZY 区需要，绿区/红区留空
     */
    @Schema(description = "L2 千问大模型裁决（MERGE / SPLIT），仅 FUZZY 区有值", example = "MERGE")
    private String l2Verdict;

    /**
     * L2 置信度（HIGH / MEDIUM / LOW）
     */
    @Schema(description = "L2 置信度", example = "HIGH")
    private String l2Confidence;

    /**
     * L2 裁决推导说明（一句话，方便人工抽查时快速理解模型判断依据）
     */
    @Schema(description = "L2 裁决原因", example = "Chunk B 是对 Chunk A 中 Redis 报错的追问延伸")
    private String l2Reason;

    /**
     * L2 调用的大模型名称（例如 qwen3.6-flash, qwen3.5-omni-flash, qwen3.5-omni-plus 等）
     */
    @Schema(description = "L2 调用的大模型名称", example = "qwen3.6-flash")
    private String l2Model;

    /**
     * L1 人工核对状态（UNCHECKED / PASSED / OVERRIDDEN）
     */
    @Schema(description = "L1 人工核对状态", example = "PASSED")
    private String l1AuditStatus;

    /**
     * L1 人工核对结论（MERGE / SPLIT）
     */
    @Schema(description = "L1 人工核对结论", example = "MERGE")
    private String l1AuditVerdict;

    /**
     * L1 人工核对时间
     */
    @Schema(description = "L1 人工核对时间")
    private LocalDateTime l1AuditTime;

    /**
     * L2 人工核对状态（UNCHECKED / PASSED / OVERRIDDEN）
     */
    @Schema(description = "L2 人工核对状态", example = "UNCHECKED")
    private String l2AuditStatus;

    /**
     * L2 人工核对结论（MERGE / SPLIT）
     */
    @Schema(description = "L2 人工核对结论", example = "MERGE")
    private String l2AuditVerdict;

    /**
     * L2 人工核对时间
     */
    @Schema(description = "L2 人工核对时间")
    private LocalDateTime l2AuditTime;

    /**
     * 人工复核备注说明
     */
    @Schema(description = "人工复核备注说明", example = "经人工核对，该节点属于跨天追问，确认为切断点")
    private String manualRemark;

    /**
     * 最终决策（MERGE / SPLIT）
     * 绿区：直接等于 MERGE；红区：直接等于 SPLIT；模糊区：等于 L2 裁决结果
     */
    @Schema(description = "最终切分决策（MERGE / SPLIT）", example = "MERGE")
    private String finalDecision;

    /**
     * 处理状态（对应 ProcessStatusEnum）
     * PENDING：等待 L2 处理；DONE：已完成；NEED_MANUAL_REVIEW：需人工复核
     */
    @Schema(description = "处理状态（PENDING / DONE / NEED_MANUAL_REVIEW）", example = "DONE")
    private String processStatus;

    /**
     * 记录创建时间（阿里规约必备三字段之一）
     */
    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;

    /**
     * 记录更新时间（阿里规约必备三字段之一）
     */
    @Schema(description = "记录更新时间")
    private LocalDateTime updateTime;
}
