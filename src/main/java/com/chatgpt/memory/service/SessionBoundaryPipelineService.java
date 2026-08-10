package com.chatgpt.memory.service;

import com.chatgpt.memory.common.enums.L1ZoneEnum;
import com.chatgpt.memory.common.enums.ProcessStatusEnum;
import com.chatgpt.memory.common.enums.PromptTemplateEnum;
import com.chatgpt.memory.integration.qwen.QwenClient;
import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ConversationPairDetail;
import com.chatgpt.memory.model.VectorSegmentationResult;
import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 会话切分三阶段流水线服务类
 * <p>
 * 实现 L1 向量初筛落库 (Stage 1) -> L2 千问精排裁决 (Stage 2) -> Stage 3 最终切分点组装的三阶段流水线。
 * 遵循“宁愿模糊也不要错拆分”的核心原则，在模型置信度低或解析失败时安全降级。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionBoundaryPipelineService {

    /** 消息边界决策过程表 DAO 接口 */
    private final SessionBoundaryPairMapper sessionBoundaryPairMapper;

    /** L1 BGE 向量切分初筛服务 */
    private final VectorSessionSplitter vectorSessionSplitter;

    /** 通义千问大模型客户端 */
    private final QwenClient qwenClient;

    /** JSON 序列化与反序列化工具 */
    private final ObjectMapper objectMapper;

    /** L1 强相关判定高门限（>= 0.82 判为 GREEN_MERGE） */
    @Value("${chat.segmentation.l1-high-threshold:0.82}")
    private double highThreshold;

    /** L1 跨主题打断低门限（< 0.60 判为 RED_SPLIT） */
    @Value("${chat.segmentation.l1-low-threshold:0.60}")
    private double lowThreshold;

    /** Stage 2 批处理拉取记录条数 */
    private static final int STAGE2_BATCH_SIZE = 50;

    /** 消息文本正文保留最大字数（拓展至 4000 字支持完整长文本与多模态图片渲染） */
    private static final int MSG_TRUNCATE_LENGTH = 4000;

    /**
     * 初始化创建数据库表（若不存在）
     */
    public void initTable() {
        sessionBoundaryPairMapper.createTableIfNotExists();
        log.info("[Init] session_boundary_pair 表初始化完毕。");
    }

    /**
     * Stage 1：计算 Pair 向量相似度并批量落库
     *
     * @param conversations 全量对话列表
     * @return 成功落库的 Pair 记录条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int runStage1(final List<ChatConversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            log.warn("[Stage1] 输入对话列表为空，跳过处理。");
            return 0;
        }

        int totalInserted = 0;
        for (final ChatConversation conversation : conversations) {
            if (conversation == null || conversation.getConversationId() == null) {
                continue;
            }

            final String convId = conversation.getConversationId();
            final VectorSegmentationResult result = vectorSessionSplitter.inspectConversation(conversation);
            if (result == null || result.getPairDetails() == null || result.getPairDetails().isEmpty()) {
                log.info("[Stage1] 对话 {} 无可分析 Pair，跳过。", convId);
                continue;
            }

            final List<SessionBoundaryPairDO> doList = new ArrayList<>(result.getPairDetails().size());
            for (final ConversationPairDetail pair : result.getPairDetails()) {
                doList.add(buildPairDO(convId, pair));
            }

            if (!doList.isEmpty()) {
                final int inserted = sessionBoundaryPairMapper.batchInsert(doList);
                totalInserted += inserted;
                log.info("[Stage1] 对话 {} 成功落库 {} 条 Pair 记录。", convId, inserted);
            }
        }

        log.info("[Stage1] 执行完成，共落库 {} 条 Pair 记录。", totalInserted);
        return totalInserted;
    }

    /**
     * Stage 1：增量/按需计算 Pair 向量相似度并落库（提升 Web 端秒级响应速度）
     *
     * @param conversations 全量对话列表
     * @param targetCount   本次目标增量落库 Pair 条数上限
     * @return 本次成功落库的 Pair 记录条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int runStage1Incremental(final List<ChatConversation> conversations, final int targetCount) {
        if (conversations == null || conversations.isEmpty()) {
            return 0;
        }

        int totalInserted = 0;
        for (final ChatConversation conversation : conversations) {
            if (conversation == null || conversation.getConversationId() == null) {
                continue;
            }

            final String convId = conversation.getConversationId();
            // 检查数据库：如果当前 Conversation 已算过落库，跳过
            final List<SessionBoundaryPairDO> existing = sessionBoundaryPairMapper.selectByConvId(convId);
            if (existing != null && !existing.isEmpty()) {
                continue;
            }

            final VectorSegmentationResult result = vectorSessionSplitter.inspectConversation(conversation);
            if (result == null || result.getPairDetails() == null || result.getPairDetails().isEmpty()) {
                continue;
            }

            final List<SessionBoundaryPairDO> doList = new ArrayList<>(result.getPairDetails().size());
            for (final ConversationPairDetail pair : result.getPairDetails()) {
                doList.add(buildPairDO(convId, pair));
            }

            if (!doList.isEmpty()) {
                final int inserted = sessionBoundaryPairMapper.batchInsert(doList);
                totalInserted += inserted;
                log.info("[Stage1Incremental] 对话 {} 成功物理算分落库 {} 条 Pair。", convId, inserted);
            }

            if (totalInserted >= targetCount) {
                break;
            }
        }

        return totalInserted;
    }

    /**
     * 构建 Pair 数据库持久化实体
     *
     * @param convId 所属对话 ID
     * @param pair   Pair 详细比对数据
     * @return 实体 DO
     */
    private SessionBoundaryPairDO buildPairDO(final String convId, final ConversationPairDetail pair) {
        final double score = pair.getScore();
        final L1ZoneEnum zone;
        final String finalDecision;
        final String processStatus;

        // 根据双门限划分大区与初始决策
        if (score >= highThreshold) {
            zone = L1ZoneEnum.GREEN_MERGE;
            finalDecision = "MERGE";
            processStatus = ProcessStatusEnum.DONE.getCode();
        } else if (score >= lowThreshold) {
            zone = L1ZoneEnum.FUZZY;
            finalDecision = null;
            processStatus = ProcessStatusEnum.PENDING.getCode();
        } else {
            zone = L1ZoneEnum.RED_SPLIT;
            finalDecision = "SPLIT";
            processStatus = ProcessStatusEnum.DONE.getCode();
        }

        final String textA = truncateText(pair.getTextA(), MSG_TRUNCATE_LENGTH);
        final String textB = truncateText(pair.getTextB(), MSG_TRUNCATE_LENGTH);
        final boolean hasAtt = (textA != null && (textA.contains("![图片]") || textA.contains("/chat-assets/")))
                            || (textB != null && (textB.contains("![图片]") || textB.contains("/chat-assets/")));

        return SessionBoundaryPairDO.builder()
                .parentConversationId(convId)
                .pairIndex(pair.getIndex())
                .messageAId("")
                .messageBId("")
                .messageARole(Objects.requireNonNullElse(pair.getRoleA(), ""))
                .messageBRole(Objects.requireNonNullElse(pair.getRoleB(), ""))
                .messageAText(textA)
                .messageBText(textB)
                .hasAttachment(hasAtt ? 1 : 0)
                .l1Score(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP))
                .l1Zone(zone.getCode())
                .finalDecision(finalDecision)
                .processStatus(processStatus)
                .build();
    }

    /**
     * Stage 2：扫描 PENDING 状态记录并调用千问模型精排
     *
     * @return 批次精排完成的 Pair 记录条数
     */
    public int runStage2() {
        int totalProcessed = 0;
        List<SessionBoundaryPairDO> batch;

        do {
            batch = sessionBoundaryPairMapper.selectPendingList(STAGE2_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            log.info("[Stage2] 开始处理本批 {} 条 PENDING 记录。", batch.size());
            for (final SessionBoundaryPairDO pairDO : batch) {
                processOnePair(pairDO);
                totalProcessed++;
            }
        } while (batch.size() == STAGE2_BATCH_SIZE);

        log.info("[Stage2] 精排完成，共处理 {} 条模糊区 Pair。", totalProcessed);
        return totalProcessed;
    }

    /**
     * 单条 Pair 调用大模型裁决及处理结果回填
     *
     * @param pairDO 待处理 Pair 实体
     */
    private void processOnePair(final SessionBoundaryPairDO pairDO) {
        final PromptTemplateEnum template = PromptTemplateEnum.L2_FUZZY_SESSION_SPLIT;
        final String userPrompt = String.format(
                template.getUserPromptTemplate(),
                pairDO.getL1Score().toPlainString(),
                pairDO.getMessageAText(),
                pairDO.getMessageBText());

        String l2Verdict;
        String l2Confidence;
        String l2Reason;
        String finalDecision;
        String processStatus;

        try {
            final String rawResponse = qwenClient.generateWithSystem(template.getSystemPrompt(), userPrompt);
            log.debug("[Stage2] Pair {} 千问响应原文: {}", pairDO.getId(), rawResponse);

            final JsonNode node = objectMapper.readTree(rawResponse);
            l2Verdict    = getTextSafe(node, "action");
            l2Confidence = getTextSafe(node, "confidence");
            l2Reason     = getTextSafe(node, "reason");

            // 置信度较低时转入人工复核队列，按默认规则倾向 MERGE
            if ("LOW".equalsIgnoreCase(l2Confidence)) {
                log.warn("[Stage2] Pair {} 置信度过低(LOW)，转入人工复核。", pairDO.getId());
                finalDecision = "MERGE";
                processStatus = ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode();
            } else {
                finalDecision = "SPLIT".equalsIgnoreCase(l2Verdict) ? "SPLIT" : "MERGE";
                processStatus = ProcessStatusEnum.DONE.getCode();
            }
        } catch (Exception e) {
            // 解析失败按原则安全降级为 MERGE
            log.error("[Stage2] Pair {} 解析异常，安全降级为 MERGE。错误: {}", pairDO.getId(), e.getMessage());
            l2Verdict    = "MERGE";
            l2Confidence = "LOW";
            l2Reason     = "JSON 解析失败，安全降级";
            finalDecision = "MERGE";
            processStatus = ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode();
        }

        sessionBoundaryPairMapper.updateL2Result(
                pairDO.getId(), l2Verdict, l2Confidence, l2Reason, finalDecision, processStatus);
        log.info("[Stage2] Pair {} 裁决完成 -> 结果: {}, 置信度: {}, 状态: {}",
                pairDO.getId(), l2Verdict, l2Confidence, processStatus);
    }

    /**
     * 安全提取 JsonNode 文本属性
     *
     * @param node      JSON 节点
     * @param fieldName 属性名称
     * @return 字符串文本
     */
    private String getTextSafe(final JsonNode node, final String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return "";
        }
        final JsonNode fn = node.get(fieldName);
        return fn.isNull() ? "" : fn.asText("");
    }

    /**
     * Stage 3：按 finalDecision 组装切分切断点索引列表
     *
     * @param parentConversationId 原始对话 ID
     * @return 切断点 Pair 序号列表
     */
    public List<Integer> getSessionSplitIndices(final String parentConversationId) {
        Objects.requireNonNull(parentConversationId, "parentConversationId 不能为 null");
        final List<SessionBoundaryPairDO> pairs =
                sessionBoundaryPairMapper.selectByConvId(parentConversationId);

        if (pairs == null || pairs.isEmpty()) {
            log.warn("[Stage3] 对话 {} 未查询到 Pair 记录。", parentConversationId);
            return Collections.emptyList();
        }

        final List<Integer> splitIndices = new ArrayList<>();
        for (final SessionBoundaryPairDO pair : pairs) {
            if ("SPLIT".equals(pair.getFinalDecision())) {
                splitIndices.add(pair.getPairIndex());
            }
        }

        log.info("[Stage3] 对话 {} 共识别出 {} 个切断位置。", parentConversationId, splitIndices.size());
        return splitIndices;
    }

    /**
     * 精准临界区 Pair 查询接口（用于人工抽查）
     *
     * @param l1Zone   L1 大区编码
     * @param scoreMin 最低相似度得分
     * @param scoreMax 最高相似度得分
     * @return 匹配的 Pair 实体列表
     */
    public List<SessionBoundaryPairDO> inspectBoundaryPairs(
            final String l1Zone, final double scoreMin, final double scoreMax) {
        return sessionBoundaryPairMapper.selectByZoneAndScoreRange(l1Zone, scoreMin, scoreMax);
    }

    /**
     * 获取需人工复核的 Pair 队列列表
     *
     * @return 人工复核队列列表
     */
    public List<SessionBoundaryPairDO> getManualReviewQueue() {
        return sessionBoundaryPairMapper.selectNeedManualReviewList();
    }

    /**
     * 查询决策过程记录总条数
     *
     * @return 总记录条数
     */
    public long countTotal() {
        return sessionBoundaryPairMapper.count();
    }

    /**
     * 安全截取多余文本
     *
     * @param text      原文本
     * @param maxLength 截取长度
     * @return 截取后文本
     */
    private static String truncateText(final String text, final int maxLength) {
        if (text == null) {
            return "";
        }
        final String trimmed = text.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}