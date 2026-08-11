package com.chatgpt.memory.service;

import com.chatgpt.memory.common.enums.L1ZoneEnum;
import com.chatgpt.memory.common.enums.LlmModelEnum;
import com.chatgpt.memory.common.enums.ProcessStatusEnum;
import com.chatgpt.memory.common.enums.PromptTemplateEnum;
import com.chatgpt.memory.integration.qwen.QwenClient;
import com.chatgpt.memory.integration.qwen.QwenModelFactory;
import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ConversationPairDetail;
import com.chatgpt.memory.model.VectorSegmentationResult;
import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;
import com.chatgpt.memory.util.ImageBase64Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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

    /** 通义千问模型动态构建与缓存工厂 */
    private final QwenModelFactory qwenModelFactory;

    /** JSON 序列化与反序列化工具 */
    private final ObjectMapper objectMapper;

    /** L1 强相关判定高门限（>= 0.82 判为 GREEN_MERGE） */
    @Value("${chat.segmentation.l1-high-threshold:0.82}")
    private double highThreshold;

    /** L1 跨主题打断低门限（< 0.60 判为 RED_SPLIT） */
    @Value("${chat.segmentation.l1-low-threshold:0.60}")
    private double lowThreshold;

    /** 启动时是否自动运行 Stage 2 L2 精排推导 */
    @Value("${chat.segmentation.l2-auto-run-enabled:false}")
    private boolean autoRunEnabled;

    /** 启动时自动运行的定量处理上限条数 */
    @Value("${chat.segmentation.l2-auto-run-limit:100}")
    private int autoRunLimit;

    /** 启动时自动运行时是否强制覆盖已有 L2 裁决 */
    @Value("${chat.segmentation.l2-force-overwrite:true}")
    private boolean autoRunForceOverwrite;

    /** Stage 2 批处理拉取记录条数 */
    private static final int STAGE2_BATCH_SIZE = 50;

    /** 消息文本正文保留最大字数（拓展至 4000 字支持完整长文本与多模态图片渲染） */
    private static final int MSG_TRUNCATE_LENGTH = 4000;

    /**
     * 应用启动完毕后自动触发 Stage 2 L2 精排推导（若配置开启）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (autoRunEnabled) {
            log.info("[Startup] 检测到 chat.segmentation.l2-auto-run-enabled=true，应用启动后自动触发 Stage 2 精排 (limit={}, forceOverwrite={})...",
                    autoRunLimit > 0 ? autoRunLimit : "UNLIMITED", autoRunForceOverwrite);
            CompletableFuture.runAsync(() -> {
                try {
                    initTable();
                    final int processed = runStage2(null, null, autoRunForceOverwrite, autoRunLimit);
                    log.info("[Startup] 自动 Stage 2 精排推导任务顺利完成，共处理 {} 条 Pair 记录。", processed);
                } catch (Exception e) {
                    log.error("[Startup] 自动 Stage 2 精排推导发生异常", e);
                }
            });
        }
    }

    /**
     * 初始化创建数据库表（若不存在则建表，若表已存在则平滑校验补全列）
     */
    public void initTable() {
        sessionBoundaryPairMapper.createTableIfNotExists();
        try {
            sessionBoundaryPairMapper.addColumnL2ModelIfNotExists();
        } catch (Exception e) {
            log.debug("[Init] l2_model 列补全检查完毕 (已存在或跳过)。");
        }
        log.info("[Init] session_boundary_pair 表与列结构初始化完毕。");
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
     * Stage 2：扫描 PENDING / FUZZY 状态记录并调用千问模型精排（默认分流模式，只处理未裁决记录）
     *
     * @return 批次精排完成的 Pair 记录条数
     */
    public int runStage2() {
        return runStage2(null, null, false, Integer.MAX_VALUE);
    }

    /**
     * Stage 2：扫描模糊区记录并调用大模型精排（支持自定义指定模型与强行覆盖重推导）
     *
     * @param customTextModel       自定义文本模型枚举（可为空，为空则使用系统默认 QWEN_TEXT_FLASH）
     * @param customMultimodalModel 自定义多模态模型枚举（可为空，为空则使用系统默认 QWEN_OMNI_FLASH）
     * @param forceOverwrite        是否强行覆盖已有的 L2 裁决记录（true：重新推导全量 FUZZY 区 Pair；false：仅推导未裁决的 FUZZY 区 Pair）
     * @return 批次精排完成的 Pair 记录条数
     */
    public int runStage2(final LlmModelEnum customTextModel,
                         final LlmModelEnum customMultimodalModel,
                         final boolean forceOverwrite) {
        return runStage2(customTextModel, customMultimodalModel, forceOverwrite, Integer.MAX_VALUE);
    }

    /**
     * Stage 2：扫描模糊区记录并调用大模型精排（支持自定义指定模型、强行覆盖与定量上限截断）
     *
     * @param customTextModel       自定义文本模型枚举（可为空，默认 QWEN_TEXT_FLASH）
     * @param customMultimodalModel 自定义多模态模型枚举（可为空，默认 QWEN_OMNI_FLASH）
     * @param forceOverwrite        是否强行覆盖已有的 L2 裁决记录（true：重新推导全量 FUZZY 区 Pair；false：仅推导未裁决的 FUZZY 区 Pair）
     * @param maxCount              定量处理最大记录条数上限（例如 100 条）
     * @return 批次精排完成的 Pair 记录条数
     */
    public int runStage2(final LlmModelEnum customTextModel,
                         final LlmModelEnum customMultimodalModel,
                         final boolean forceOverwrite,
                         final int maxCount) {
        final int targetLimit = maxCount > 0 ? maxCount : Integer.MAX_VALUE;
        int totalProcessed = 0;
        Long lastId = 0L;
        List<SessionBoundaryPairDO> batch;

        do {
            final int currentBatchSize = Math.min(STAGE2_BATCH_SIZE, targetLimit - totalProcessed);
            if (currentBatchSize <= 0) {
                break;
            }

            batch = sessionBoundaryPairMapper.selectL2ProcessList(forceOverwrite, lastId, currentBatchSize);
            if (batch.isEmpty()) {
                break;
            }

            log.info("[Stage2] 开始处理本批 {} 条模糊区记录 (lastId={}, forceOverwrite={}, 已完成 {}/{} 条)。",
                    batch.size(), lastId, forceOverwrite, totalProcessed, targetLimit);

            try {
                for (final SessionBoundaryPairDO pairDO : batch) {
                    processOnePair(pairDO, customTextModel, customMultimodalModel);
                    totalProcessed++;
                    lastId = pairDO.getId();
                    if (totalProcessed >= targetLimit) {
                        break;
                    }
                }
            } catch (com.chatgpt.memory.common.exception.LlmApiException e) {
                log.error("[Stage2] 捕获大模型 API 不可恢复异常，批次处理在 Pair {} 强行熔断终止！已完成 {} 条。异常: {}",
                        lastId, totalProcessed, e.getMessage());
                throw e;
            }
        } while (totalProcessed < targetLimit && batch.size() == STAGE2_BATCH_SIZE);

        log.info("[Stage2] L2 精排裁决完成，共定量处理 {} 条 Pair 记录。", totalProcessed);
        return totalProcessed;
    }

    /**
     * 单条 Pair 调用大模型裁决及处理结果回填（默认路由模型）
     *
     * @param pairDO 待处理 Pair 实体
     */
    private void processOnePair(final SessionBoundaryPairDO pairDO) {
        processOnePair(pairDO, null, null);
    }

    /**
     * 单条 Pair 调用大模型裁决及处理结果回填（支持纯文本与多模态模型分流路由与模型覆写）
     *
     * @param pairDO                待处理 Pair 实体
     * @param customTextModel       自定义文本模型枚举（可选）
     * @param customMultimodalModel 自定义多模态模型枚举（可选）
     */
    private void processOnePair(final SessionBoundaryPairDO pairDO,
                                final LlmModelEnum customTextModel,
                                final LlmModelEnum customMultimodalModel) {
        final boolean isHasAttachment = Integer.valueOf(1).equals(pairDO.getHasAttachment());
        final List<String> imagesA = isHasAttachment ? ImageBase64Util.extractBase64Images(pairDO.getMessageAText()) : Collections.emptyList();
        final List<String> imagesB = isHasAttachment ? ImageBase64Util.extractBase64Images(pairDO.getMessageBText()) : Collections.emptyList();
        final boolean isMultimodal = isHasAttachment && (!imagesA.isEmpty() || !imagesB.isEmpty());

        final PromptTemplateEnum template = isMultimodal
                ? PromptTemplateEnum.L2_MULTIMODAL_SESSION_SPLIT
                : PromptTemplateEnum.L2_FUZZY_SESSION_SPLIT;

        final LlmModelEnum defaultModel = isMultimodal
                ? LlmModelEnum.QWEN_3_VL_PLUS
                : LlmModelEnum.QWEN_36_FLASH_SNAPSHOT;

        final LlmModelEnum modelEnum = isMultimodal
                ? (customMultimodalModel != null ? customMultimodalModel : defaultModel)
                : (customTextModel != null ? customTextModel : defaultModel);

        final ChatLanguageModel model = qwenModelFactory.getModel(modelEnum);

        final String l1ScoreStr = pairDO.getL1Score() != null ? pairDO.getL1Score().toPlainString() : "0.0000";
        final String userPromptText = String.format(
                template.getUserPromptTemplate(),
                l1ScoreStr,
                pairDO.getMessageAText(),
                pairDO.getMessageBText());

        String l2Verdict;
        String l2Confidence;
        String l2Reason;
        String finalDecision;
        String processStatus;

        Response<AiMessage> response;
        boolean isImageFallback = isHasAttachment && (imagesA.isEmpty() && imagesB.isEmpty());
        try {
            if (isMultimodal) {
                final List<Content> contents = new ArrayList<>();

                // 1. 注入 Chunk A 的文本与关联图片
                final String headerA = String.format(
                        "向量相似度得分：%s (划归边界评估区)\n\n【对话片段 A (前文结尾约 200 字)】：\n%s",
                        l1ScoreStr, pairDO.getMessageAText());
                contents.add(TextContent.from(headerA));

                for (final String dataUri : imagesA) {
                    contents.add(ImageContent.from(dataUri));
                }

                // 2. 注入 Chunk B 的文本与关联图片
                final String headerB = String.format(
                        "\n\n【对话片段 B (当前上下文开头约 200 字)】：\n%s",
                        pairDO.getMessageBText());
                contents.add(TextContent.from(headerB));

                for (final String dataUri : imagesB) {
                    contents.add(ImageContent.from(dataUri));
                }

                // 3. 注入裁决引导提示词
                contents.add(TextContent.from(
                        "\n\n【裁决任务说明】：\n请结合上述紧跟文本顺序的 Chunk A 及其图片与 Chunk B 及其图片，判断 Chunk B 是否与 Chunk A 属于同一会话上下文，并按指定 JSON 格式输出判定结果。"
                ));

                log.info("[Stage2-Request] Pair {} -> 调起全模态模型 [{}] | Prompt: {} | Chunk A 图片: {} 张, Chunk B 图片: {} 张 | Chunk A 文本预览: [{}] | Chunk B 文本预览: [{}]",
                        pairDO.getId(), modelEnum.getModelName(), template.getCode(), imagesA.size(), imagesB.size(),
                        truncateText(pairDO.getMessageAText(), 80), truncateText(pairDO.getMessageBText(), 80));

                response = model.generate(
                        SystemMessage.from(template.getSystemPrompt()),
                        UserMessage.from(contents)
                );
            } else {
                log.info("[Stage2-Request] Pair {} -> 调起纯语言模型 [{}] | Prompt: {} | Chunk A 文本预览: [{}] | Chunk B 文本预览: [{}]",
                        pairDO.getId(), modelEnum.getModelName(), template.getCode(),
                        truncateText(pairDO.getMessageAText(), 80), truncateText(pairDO.getMessageBText(), 80));

                response = model.generate(
                        SystemMessage.from(template.getSystemPrompt()),
                        UserMessage.from(userPromptText)
                );
            }
        } catch (Exception e) {
            final String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            final boolean isImageError = errorMsg.contains("image format")
                    || errorMsg.contains("cannot be opened")
                    || errorMsg.contains("invalid_parameter_error");

            if (isMultimodal && isImageError) {
                log.warn("[Stage2] Pair {} 全模态图片格式受损或无法被模型打开，自动退守纯语言模型重试: {}", pairDO.getId(), errorMsg);
                try {
                    isImageFallback = true;
                    final PromptTemplateEnum textTemplate = PromptTemplateEnum.L2_FUZZY_SESSION_SPLIT;
                    final LlmModelEnum textModelEnum = customTextModel != null ? customTextModel : LlmModelEnum.QWEN_36_FLASH_SNAPSHOT;
                    final ChatLanguageModel textModel = qwenModelFactory.getModel(textModelEnum);

                    final String fallbackPromptText = String.format(
                            textTemplate.getUserPromptTemplate(),
                            l1ScoreStr,
                            pairDO.getMessageAText(),
                            pairDO.getMessageBText());

                    log.info("[Stage2-Request] Pair {} -> (降级退守) 调起纯语言模型 [{}]", pairDO.getId(), textModelEnum.getModelName());
                    response = textModel.generate(
                            SystemMessage.from(textTemplate.getSystemPrompt()),
                            UserMessage.from(fallbackPromptText)
                    );
                } catch (Exception fallbackEx) {
                    log.error("[Stage2] Pair {} 降级退守纯语言模型重试失败: {}", pairDO.getId(), fallbackEx.getMessage());
                    throw new com.chatgpt.memory.common.exception.LlmApiException("Pair " + pairDO.getId() + " 模型调用失败: " + fallbackEx.getMessage(), fallbackEx);
                }
            } else {
                log.error("[Stage2] Pair {} 调起大模型 API 失败，触发强行熔断阻断: {}", pairDO.getId(), errorMsg);
                throw new com.chatgpt.memory.common.exception.LlmApiException("Pair " + pairDO.getId() + " 模型调用失败: " + errorMsg, e);
            }
        }

        try {
            final String rawResponse = (response != null && response.content() != null)
                    ? response.content().text()
                    : "";
            log.info("[Stage2-Response] Pair {} -> 收到模型原始响应: {}", pairDO.getId(), rawResponse);

            String jsonText = rawResponse != null ? rawResponse.trim() : "";
            if (jsonText.startsWith("```")) {
                final int firstNewline = jsonText.indexOf('\n');
                final int lastBacktick = jsonText.lastIndexOf("```");
                if (firstNewline != -1 && lastBacktick > firstNewline) {
                    jsonText = jsonText.substring(firstNewline + 1, lastBacktick).trim();
                }
            }

            final JsonNode node = objectMapper.readTree(jsonText);
            l2Verdict    = getTextSafe(node, "action");
            l2Confidence = getTextSafe(node, "confidence");
            l2Reason     = getTextSafe(node, "reason");

            if (isImageFallback) {
                log.warn("[Stage2] Pair {} 因图片解析受损退守纯语言模型，强制归为 LOW 置信度与待人审(NEED_MANUAL_REVIEW)状态。", pairDO.getId());
                l2Confidence = "LOW";
                l2Reason = "[图片解析受损退守纯文本] " + (l2Reason != null ? l2Reason : "");
                finalDecision = "MERGE";
                processStatus = ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode();
            } else if ("LOW".equalsIgnoreCase(l2Confidence)) {
                log.warn("[Stage2] Pair {} 置信度过低(LOW)，转入人工复核。", pairDO.getId());
                finalDecision = "MERGE";
                processStatus = ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode();
            } else {
                finalDecision = "SPLIT".equalsIgnoreCase(l2Verdict) ? "SPLIT" : "MERGE";
                processStatus = ProcessStatusEnum.DONE.getCode();
            }
        } catch (Exception e) {
            log.error("[Stage2] Pair {} 响应文本 JSON 解析异常，安全降级为 MERGE/LOW。错误: {}", pairDO.getId(), e.getMessage());
            l2Verdict    = "MERGE";
            l2Confidence = "LOW";
            l2Reason     = isImageFallback ? "[图片解析受损退守纯文本] JSON 解析失败" : "JSON 解析失败，安全降级";
            finalDecision = "MERGE";
            processStatus = ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode();
        }

        final String actualModelName = isImageFallback
                ? (customTextModel != null ? customTextModel.getModelName() : LlmModelEnum.QWEN_36_FLASH_SNAPSHOT.getModelName())
                : modelEnum.getModelName();

        sessionBoundaryPairMapper.updateL2Result(
                pairDO.getId(), l2Verdict, l2Confidence, l2Reason, actualModelName, finalDecision, processStatus);
        log.info("[Stage2] Pair {} L2 裁决完成 -> 结果: {}, 置信度: {}, 模型: {}, 状态: {}",
                pairDO.getId(), l2Verdict, l2Confidence, actualModelName, processStatus);
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