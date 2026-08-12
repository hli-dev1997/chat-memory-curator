package com.chatgpt.memory.mapper;

import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * session_boundary_pair 数据库表 MyBatis 映射 Mapper 接口
 * <p>
 * 提供消息边界决策过程表的批量写入、状态扫描、临界区查询等数据访问能力。
 * 主要服务于三阶段流水线：
 * <ul>
 *   <li>Stage 1：批量插入 L1 向量计算结果（batchInsert）</li>
 *   <li>Stage 2：扫描 PENDING 状态的模糊区 Pair，执行 L2 裁决后更新（selectPendingList + updateL2Result）</li>
 *   <li>Stage 3：按对话 ID 有序查询全量 Pair，组装 final_decision 生成 ChatSession（selectByConvId）</li>
 *   <li>人工抽查：按区间 + 得分范围检索高危临界 Pair（selectByZoneAndScoreRange）</li>
 * </ul>
 * </p>
 *
 * @author Antigravity
 */
@Mapper
public interface SessionBoundaryPairMapper {

    /**
     * 初始化建表（若不存在）
     */
    void createTableIfNotExists();

    /**
     * 动态平滑补全 l2_model 列（用于旧数据表升级兼容）
     */
    void addColumnL2ModelIfNotExists();

    /**
     * 批量插入 Pair 记录（Stage 1 落库使用）
     *
     * @param list Pair 实体列表
     * @return 影响行数
     */
    int batchInsert(@Param("list") List<SessionBoundaryPairDO> list);

    /**
     * 查询指定对话的全量 Pair 记录（按 pair_index 升序排列，Stage 3 组装 Session 使用）
     *
     * @param parentConversationId 对话 ID
     * @return 有序 Pair 列表
     */
    List<SessionBoundaryPairDO> selectByConvId(@Param("parentConversationId") String parentConversationId);

    /**
     * 查询指定数量的 PENDING 状态 Pair（Stage 2 扫描模糊区，批量调用 L2 千问使用）
     *
     * @param limit 每批拉取的最大条数（防止一次性拉取量过大）
     * @return PENDING 状态的 Pair 列表
     */
    List<SessionBoundaryPairDO> selectPendingList(@Param("limit") int limit);

    /**
     * 查询 Stage 2 待处理的 Pair 列表（支持大区筛选、游标分页、增量推导与全量覆盖重推导）
     *
     * @param l1Zone         大区筛选（FUZZY / GREEN_MERGE / RED_SPLIT / ALL 或 null 表示全量）
     * @param forceOverwrite 是否强行覆盖已有的 L2 裁决记录
     * @param lastId         上一批最后一条 Pair 的 ID（0L 表示从头开始）
     * @param limit          每批拉取的最大条数
     * @return 待推导的 Pair 列表
     */
    List<SessionBoundaryPairDO> selectL2ProcessList(
            @Param("l1Zone") String l1Zone,
            @Param("forceOverwrite") boolean forceOverwrite,
            @Param("lastId") Long lastId,
            @Param("limit") int limit
    );

    /**
     * 查询 Stage 2 待处理的 Pair 列表（三参数默认重载方法，默认全量大区）
     */
    default List<SessionBoundaryPairDO> selectL2ProcessList(
            boolean forceOverwrite,
            Long lastId,
            int limit) {
        return selectL2ProcessList("ALL", forceOverwrite, lastId, limit);
    }

    /**
     * 统计 L1 向量模型的裁决准确度及误合并/误切分混淆矩阵指标
     *
     * @return 包含 totalEvaluated, l1AccuracyRate, falseMergeCount, falseSplitCount 等指标的 Map
     */
    Map<String, Object> selectL1AccuracyStats();

    /**
     * 条件分页查询已完成 L2 大模型精排的 Pair 记录（支持大区过滤）
     *
     * @param hasAttachment  是否有附件（null 不限，1有图，0无图）
     * @param processStatus  处理状态（null 不限）
     * @param l2Verdict      L2 裁决结论（null 不限，MERGE / SPLIT）
     * @param l2Confidence   置信度（null 不限，HIGH / MEDIUM / LOW）
     * @param l1Zone         L1 大区过滤（null 不限，FUZZY / GREEN_MERGE / RED_SPLIT）
     * @param keyword        关键词搜索（可为空）
     * @param offset         偏移量
     * @param limit          拉取条数
     * @return L2 裁决记录列表
     */
    List<SessionBoundaryPairDO> selectL2Records(
            @Param("hasAttachment") Integer hasAttachment,
            @Param("processStatus") String processStatus,
            @Param("l2Verdict") String l2Verdict,
            @Param("l2Confidence") String l2Confidence,
            @Param("l1Zone") String l1Zone,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 条件分页查询已完成 L2 大模型精排的 Pair 记录（不限大区的兼容方法）
     */
    default List<SessionBoundaryPairDO> selectL2Records(
            Integer hasAttachment,
            String processStatus,
            String l2Verdict,
            String l2Confidence,
            String keyword,
            int offset,
            int limit) {
        return selectL2Records(hasAttachment, processStatus, l2Verdict, l2Confidence, null, keyword, offset, limit);
    }

    /**
     * 统计匹配条件的 L2 裁决记录条数
     */
    int countL2Records(
            @Param("hasAttachment") Integer hasAttachment,
            @Param("processStatus") String processStatus,
            @Param("l2Verdict") String l2Verdict,
            @Param("l2Confidence") String l2Confidence,
            @Param("l1Zone") String l1Zone,
            @Param("keyword") String keyword
    );

    /**
     * 统计匹配条件的 L2 裁决记录条数（不限大区的兼容方法）
     */
    default int countL2Records(
            Integer hasAttachment,
            String processStatus,
            String l2Verdict,
            String l2Confidence,
            String keyword) {
        return countL2Records(hasAttachment, processStatus, l2Verdict, l2Confidence, null, keyword);
    }

    /**
     * 统计 FUZZY 模糊区与 L2 待处理/已处理总指标看板数据
     *
     * @return 包含 totalFuzzy, totalFuzzyImages, totalFuzzyText, totalJudged, totalPending, totalMerge, totalSplit, totalReview 的 Map
     */
    Map<String, Object> selectL2SummaryStats();

    /**
     * 回填 L2 裁决结果（Stage 2 裁决完成后更新）
     *
     * @param id            主键 ID
     * @param l2Verdict     L2 裁决（MERGE / SPLIT）
     * @param l2Confidence  L2 置信度（HIGH / MEDIUM / LOW）
     * @param l2Reason      L2 裁决依据说明
     * @param l2Model       L2 调用的大模型名称
     * @param finalDecision 最终决策（等于 l2Verdict）
     * @param processStatus 处理状态（DONE / NEED_MANUAL_REVIEW）
     * @return 影响行数
     */
    int updateL2Result(
            @Param("id") Long id,
            @Param("l2Verdict") String l2Verdict,
            @Param("l2Confidence") String l2Confidence,
            @Param("l2Reason") String l2Reason,
            @Param("l2Model") String l2Model,
            @Param("finalDecision") String finalDecision,
            @Param("processStatus") String processStatus
    );

    /**
     * 按 L1 大区和得分范围查询（精准临界区人工抽查使用）
     *
     * @param l1Zone     大区编码（GREEN_MERGE / FUZZY / RED_SPLIT）
     * @param scoreMin   分数下限（含）
     * @param scoreMax   分数上限（含）
     * @return 命中的 Pair 列表
     */
    List<SessionBoundaryPairDO> selectByZoneAndScoreRange(
            @Param("l1Zone") String l1Zone,
            @Param("scoreMin") double scoreMin,
            @Param("scoreMax") double scoreMax
    );

    /**
     * 回填 L1 人工核查结果
     *
     * @param id             主键 ID
     * @param l1AuditStatus  核对状态（PASSED / OVERRIDDEN）
     * @param l1AuditVerdict 人工裁决（MERGE / SPLIT）
     * @param finalDecision  更新后的最终决策
     * @param manualRemark   人工备注说明
     * @return 影响行数
     */
    int updateL1AuditResult(
            @Param("id") Long id,
            @Param("l1AuditStatus") String l1AuditStatus,
            @Param("l1AuditVerdict") String l1AuditVerdict,
            @Param("finalDecision") String finalDecision,
            @Param("manualRemark") String manualRemark
    );

    /**
     * 回填 L2 人工核查结果
     *
     * @param id             主键 ID
     * @param l2AuditStatus  核对状态（PASSED / OVERRIDDEN）
     * @param l2AuditVerdict 人工裁决（MERGE / SPLIT）
     * @param finalDecision  更新后的最终决策
     * @param manualRemark   人工备注说明
     * @return 影响行数
     */
    int updateL2AuditResult(
            @Param("id") Long id,
            @Param("l2AuditStatus") String l2AuditStatus,
            @Param("l2AuditVerdict") String l2AuditVerdict,
            @Param("finalDecision") String finalDecision,
            @Param("manualRemark") String manualRemark
    );

    /**
     * 更新 Pair 记录的消息正文与附件标记（用于多模态图片重解析同步）
     */
    int updatePairTextAndAttachment(
            @Param("parentConversationId") String parentConversationId,
            @Param("pairIndex") Integer pairIndex,
            @Param("messageAText") String messageAText,
            @Param("messageBText") String messageBText,
            @Param("hasAttachment") Integer hasAttachment
    );

    /**
     * 流式连续审核查询：分页获取 l1_audit_status = 'UNCHECKED' 未审核的 Pair 记录流
     *
     * @param l1Zone        过滤大区（可选 GREEN_MERGE / RED_SPLIT / FUZZY）
     * @param hasAttachment 是否筛选含附件/图片记录（可选 1 代表只看含附件/图片）
     * @param limit         获取记录条数
     * @return 未核对 Pair 列表
     */
    List<SessionBoundaryPairDO> selectUncheckedStream(
            @Param("l1Zone") String l1Zone,
            @Param("hasAttachment") Integer hasAttachment,
            @Param("limit") int limit
    );

    /**
     * 统计未审核 (UNCHECKED) 的 Pair 记录总数
     *
     * @param l1Zone        过滤大区（可选）
     * @param hasAttachment 是否筛选含附件/图片记录（可选）
     * @return 未审核总数
     */
    long countUnchecked(
            @Param("l1Zone") String l1Zone,
            @Param("hasAttachment") Integer hasAttachment
    );

    /**
     * 统计已审核 (PASSED 或 OVERRIDDEN) 的 Pair 记录总数
     *
     * @return 已审核总数
     */
    long countAudited();

    /**
     * 按时间倒序拉取已完成核对的历史记录列表（用于撤销与再纠偏）
     *
     * @param limit 拉取条数
     * @return 已核对的 Pair 列表
     */
    List<SessionBoundaryPairDO> selectAuditedHistory(@Param("limit") int limit);

    /**
     * 查询 NEED_MANUAL_REVIEW 状态的记录（人工复核队列）
     *
     * @return 需人工复核的 Pair 列表
     */
    List<SessionBoundaryPairDO> selectNeedManualReviewList();

    /**
     * 查询记录总条数
     *
     * @return 总记录数
     */
    long count();
}
