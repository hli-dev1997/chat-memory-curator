package com.chatgpt.memory.controller;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.parser.ChatExportParser;
import com.chatgpt.memory.service.VectorSessionSplitter;
import com.chatgpt.memory.model.ConversationPairDetail;
import com.chatgpt.memory.model.VectorSegmentationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 类说明 / Class Description:
 * 中文：BGE 向量语义切分全量 (1,142 场) 动态可视化诊断 REST 控制器。
 * English: Dynamic REST controller for visualizing BGE vector segmentation across all 1,142 conversations.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：严格遵循《阿里巴巴 Java 开发手册（黄山版）》规范，直接委派 VectorSessionSplitter 服务获取全量切分与 Pair 得分明细。
 * 用户在 application.yml 中修改切分门限并重启服务后，可通过固定书签网址 http://localhost:520/segmentation-visualizer.html
 * 实时加载最新门限参数，无卡顿检索诊断全量对话。
 * English: Direct delegation to VectorSessionSplitter for live BGE similarity score visualization.
 * </p>
 *
 * @author Antigravity
 * @since 1.0.0
 */
import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import org.springframework.web.bind.annotation.PostMapping;

import com.chatgpt.memory.service.SessionBoundaryPipelineService;
import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;

@Slf4j
@RestController
@RequestMapping("/api/v1/segmentation-visualizer")
@RequiredArgsConstructor
@Tag(name = "SegmentationVisualizerController", description = "BGE 向量切分全量动态可视化诊断与多阶段人工复核控制器")
public class SegmentationVisualizerController {

    private static final String REAL_DATA_PATH = "e:/data/chatGPT_back/chatGPT导出20251214/chat.html";

    private final ChatExportParser chatExportParser;
    private final VectorSessionSplitter vectorSessionSplitter;
    private final SessionBoundaryPairMapper sessionBoundaryPairMapper;
    private final SessionBoundaryPipelineService sessionBoundaryPipelineService;

    @Value("${chat.segmentation.l1-high-threshold:0.82}")
    private double highThreshold;

    @Value("${chat.segmentation.l1-low-threshold:0.60}")
    private double lowThreshold;

    @Value("${chat.segmentation.short-text-min-length:30}")
    private int shortTextMinLength;

    /**
     * 一键全量预处理：将 chat.html 中所有对话的 Stage1 向量计算结果全部落库，
     * 调用一次后总量固定，审核期间不再自动增长。
     * <p>适合在正式批量审核前执行一次，确保全局总量稳定。</p>
     */
    @PostMapping("/preprocess-all")
    @Operation(summary = "一键全量预处理全部对话", description = "解析 chat.html 并将所有尚未处理的对话执行 Stage1 向量计算后批量落库，保证审核期间总量不再自动增长")
    public ResponseEntity<Map<String, Object>> preprocessAll() {
        final File file = new File(REAL_DATA_PATH);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "未找到数据源: " + REAL_DATA_PATH));
        }
        try {
            sessionBoundaryPipelineService.initTable();
            final List<com.chatgpt.memory.model.ChatConversation> conversations = chatExportParser.parseHtmlFile(file);
            final int inserted = sessionBoundaryPipelineService.runStage1Incremental(conversations, Integer.MAX_VALUE);
            final long total = sessionBoundaryPairMapper.count();
            final long unchecked = sessionBoundaryPairMapper.countUnchecked(null, null);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "全量预处理完成",
                    "newInserted", inserted,
                    "totalCount", total,
                    "uncheckedCount", unchecked
            ));
        } catch (Exception e) {
            log.error("Failed to preprocess all conversations", e);
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "预处理失败: " + e.getMessage()));
        }
    }

    private final java.util.concurrent.atomic.AtomicReference<com.chatgpt.memory.model.dto.L2TaskStatusDTO> l2TaskStatus =
            new java.util.concurrent.atomic.AtomicReference<>(
                    com.chatgpt.memory.model.dto.L2TaskStatusDTO.builder()
                            .running(false)
                            .lastError(null)
                            .failedPairId(null)
                            .processedCount(0)
                            .startTime(null)
                            .finishTime(null)
                            .build()
            );

    /**
     * 触发全量 Stage 2 千问大模型精排裁决（异步执行）
     * <p>支持选择文本与多模态模型名称，并支持强行覆盖重推导。</p>
     */
    @PostMapping("/run-stage2")
    @Operation(summary = "触发 Stage 2 千问大模型精排裁决", description = "后台异步启动 Stage 2 任务，支持指定文本/多模态模型、是否覆盖重写已裁决记录以及定量上限限制")
    public ResponseEntity<Map<String, Object>> runStage2(
            @Parameter(description = "自定义纯文本模型（枚举名如 QWEN_TEXT_FLASH 或模型名如 qwen3.6-flash）", example = "QWEN_TEXT_FLASH")
            @RequestParam(required = false) final String textModel,
            @Parameter(description = "自定义多模态模型（枚举名如 QWEN_OMNI_PLUS 或模型名如 qwen3.5-omni-plus）", example = "QWEN_OMNI_PLUS")
            @RequestParam(required = false) final String multimodalModel,
            @Parameter(description = "是否强行覆盖重推导已有记录（true是/false否）", example = "false")
            @RequestParam(defaultValue = "false") final boolean forceOverwrite,
            @Parameter(description = "定量处理最大条数上限（0表示全量，例如指定100条）", example = "100")
            @RequestParam(defaultValue = "0") final int maxCount) {

        final com.chatgpt.memory.common.enums.LlmModelEnum textEnum = parseModelEnum(textModel);
        final com.chatgpt.memory.common.enums.LlmModelEnum omniEnum = parseModelEnum(multimodalModel);

        l2TaskStatus.set(com.chatgpt.memory.model.dto.L2TaskStatusDTO.builder()
                .running(true)
                .lastError(null)
                .failedPairId(null)
                .processedCount(0)
                .startTime(java.time.LocalDateTime.now())
                .finishTime(null)
                .build());

        CompletableFuture.runAsync(() -> {
            try {
                log.info("[Controller] 开始异步触发 Stage 2 任务 | textModel: {}, omniModel: {}, forceOverwrite: {}, maxCount: {}",
                        textEnum != null ? textEnum.getModelName() : "DEFAULT",
                        omniEnum != null ? omniEnum.getModelName() : "DEFAULT",
                        forceOverwrite, maxCount > 0 ? maxCount : "UNLIMITED");
                final int processed = sessionBoundaryPipelineService.runStage2(textEnum, omniEnum, forceOverwrite, maxCount);
                log.info("[Controller] 异步 Stage 2 任务顺利完成，共精排 {} 条 Pair。", processed);

                l2TaskStatus.set(com.chatgpt.memory.model.dto.L2TaskStatusDTO.builder()
                        .running(false)
                        .lastError(null)
                        .failedPairId(null)
                        .processedCount(processed)
                        .startTime(l2TaskStatus.get().getStartTime())
                        .finishTime(java.time.LocalDateTime.now())
                        .build());
            } catch (com.chatgpt.memory.common.exception.LlmApiException e) {
                log.error("[Controller] 异步 Stage 2 任务触发大模型 API 熔断阻断！错误: {}", e.getMessage());
                l2TaskStatus.set(com.chatgpt.memory.model.dto.L2TaskStatusDTO.builder()
                        .running(false)
                        .lastError(e.getMessage())
                        .failedPairId(null)
                        .processedCount(l2TaskStatus.get() != null ? l2TaskStatus.get().getProcessedCount() : 0)
                        .startTime(l2TaskStatus.get() != null ? l2TaskStatus.get().getStartTime() : null)
                        .finishTime(java.time.LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("[Controller] 异步 Stage 2 任务执行异常", e);
                l2TaskStatus.set(com.chatgpt.memory.model.dto.L2TaskStatusDTO.builder()
                        .running(false)
                        .lastError(e.getMessage() != null ? e.getMessage() : "未知执行异常")
                        .failedPairId(null)
                        .processedCount(l2TaskStatus.get() != null ? l2TaskStatus.get().getProcessedCount() : 0)
                        .startTime(l2TaskStatus.get() != null ? l2TaskStatus.get().getStartTime() : null)
                        .finishTime(java.time.LocalDateTime.now())
                        .build());
            }
        });

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", String.format("L2 精排任务已异步启动！文本模型: %s，多模态模型: %s，强制覆盖模式: %s，目标上限: %s 条。",
                        textEnum != null ? textEnum.getModelName() : "qwen3.7-flash(默认)",
                        omniEnum != null ? omniEnum.getModelName() : "qwen3-vl-plus(默认)",
                        forceOverwrite,
                        maxCount > 0 ? maxCount : "全量")
        ));
    }

    /**
     * 获取 L2 大模型后台精排任务实时执行状态与最后一次报错详情
     */
    @GetMapping("/l2-task-status")
    @Operation(summary = "获取 L2 后台推导任务状态", description = "用于前端定时轮询或监控大模型 API 熔断阻断报错与运行状态")
    public ResponseEntity<Map<String, Object>> getL2TaskStatus() {
        final com.chatgpt.memory.model.dto.L2TaskStatusDTO status = l2TaskStatus.get();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取任务状态成功",
                "status", status != null ? status : Map.of()
        ));
    }

    /**
     * 字符串解析映射为 LlmModelEnum 枚举项
     */
    private com.chatgpt.memory.common.enums.LlmModelEnum parseModelEnum(final String modelStr) {
        if (modelStr == null || modelStr.isBlank()) {
            return null;
        }
        final String trimmed = modelStr.trim();
        for (final com.chatgpt.memory.common.enums.LlmModelEnum model : com.chatgpt.memory.common.enums.LlmModelEnum.values()) {
            if (model.name().equalsIgnoreCase(trimmed) || model.getModelName().equalsIgnoreCase(trimmed)) {
                return model;
            }
        }
        return null;
    }

    /**
     * 流式连续获取未审核 (l1_audit_status = UNCHECKED) 的 Pair 记录流
     * 若数据库为空，自动初始化执行 Stage 1 将全量 Pair 导入表
     */
    @GetMapping("/stream")
    @Operation(summary = "流式连续读取待审核 Pair 队列", description = "按 ID 顺序分批获取 l1_audit_status = 'UNCHECKED' 的 Pair 记录，支持增量计算落库与无感觉断点续看")
    public ResponseEntity<Map<String, Object>> getUncheckedStream(
            @Parameter(description = "过滤大区（可选 GREEN_MERGE / RED_SPLIT / FUZZY）", example = "FUZZY")
            @RequestParam(required = false) final String l1Zone,
            @Parameter(description = "是否仅过滤包含附件/图片的记录（可选 1）", example = "1")
            @RequestParam(required = false) final Integer hasAttachment,
            @Parameter(description = "获取流单页批次条数", example = "20")
            @RequestParam(defaultValue = "20") final int limit) {

        final File file = new File(REAL_DATA_PATH);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "未找到数据源: " + REAL_DATA_PATH));
        }

        try {
            // 首先确保数据表已创建（如果不存在则执行 DDL）
            sessionBoundaryPipelineService.initTable();

            // NOTE: 增量处理已迁移至 /api/v1/segmentation-visualizer/preprocess-all 接口，
            // stream 端点不再自动触发，防止审核过程中总量悄悄膨胀导致用户困惑。

            final List<SessionBoundaryPairDO> streamList = sessionBoundaryPairMapper.selectUncheckedStream(l1Zone, hasAttachment, limit);
            final long total = sessionBoundaryPairMapper.count();
            final long unchecked = sessionBoundaryPairMapper.countUnchecked(null, null);
            final long audited = sessionBoundaryPairMapper.countAudited();

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "获取未审核流成功",
                    "totalCount", total,
                    "uncheckedCount", unchecked,
                    "auditedCount", audited,
                    "data", streamList
            ));
        } catch (Exception e) {
            log.error("Failed to fetch unchecked stream", e);
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "获取审核流失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前系统运行生效的切分配置门限
     *
     * @return 门限配置 Map
     */
    @GetMapping("/config")
    @Operation(summary = "获取当前系统生效的切分门限配置", description = "返回 application.yml 中配置的强相关门限、切断门限及短文本判定字数")
    public ResponseEntity<Map<String, Object>> getActiveConfig() {
        final Map<String, Object> config = Map.of(
                "highThreshold", highThreshold,
                "lowThreshold", lowThreshold,
                "shortTextMinLength", shortTextMinLength
        );
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取成功",
                "data", config
        ));
    }

    /**
     * 获取全量对话列表概要（支持关键字模糊过滤）
     *
     * @param keyword 可选关键字搜索
     * @return 对话概要列表
     */
    @GetMapping("/conversations")
    @Operation(summary = "获取全量对话摘要索引列表", description = "流式解析 chat.html 并返回全量 1,142 场对话摘要，支持标题模糊匹配搜索")
    public ResponseEntity<Map<String, Object>> getConversationList(
            @Parameter(description = "搜索关键字（可选）", example = "Kafka")
            @RequestParam(required = false) final String keyword) {

        final File file = new File(REAL_DATA_PATH);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "未找到导出文件: " + REAL_DATA_PATH,
                    "data", List.of()
            ));
        }

        try {
            final List<ChatConversation> conversations = chatExportParser.parseHtmlFile(file);
            final List<Map<String, Object>> list = new ArrayList<>();

            for (final ChatConversation conv : conversations) {
                final String title = conv.getTitle() != null ? conv.getTitle() : "未命名对话";
                if (keyword != null && !keyword.isBlank()) {
                    if (!title.toLowerCase().contains(keyword.trim().toLowerCase())) {
                        continue;
                    }
                }

                final Map<String, Object> item = new HashMap<>();
                item.put("id", conv.getConversationId());
                item.put("title", title);
                item.put("messagesCount", conv.getMessages() != null ? conv.getMessages().size() : 0);
                list.add(item);
            }

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "查询成功",
                    "totalCount", conversations.size(),
                    "matchedCount", list.size(),
                    "data", list
            ));
        } catch (IOException e) {
            log.error("Failed to load conversation list from file: {}", REAL_DATA_PATH, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "解析数据失败: " + e.getMessage(),
                    "data", List.of()
            ));
        }
    }

    /**
     * 按对话 ID 动态进行 BGE 向量切分并计算 Pair 得分详情
     *
     * @param convId 对话 ID
     * @return 该对话所有 Message Pair 的 BGE 实算余弦得分及 Session 拆片结果
     */
    @GetMapping("/conversations/{convId}")
    @Operation(summary = "按对话 ID 动态计算 BGE 相似度与 Session 拆分块", description = "实时计算指定对话各语句 Pair 的 BGE 向量余弦得分，展现判定 Badge 与 Session 产物")
    public ResponseEntity<Map<String, Object>> getConversationDetail(
            @Parameter(description = "对话 ID", example = "693d2b44-ec88-8324-b330-427a95fc293a")
            @PathVariable final String convId) {

        final File file = new File(REAL_DATA_PATH);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "未找到导出文件: " + REAL_DATA_PATH
            ));
        }

        try {
            final List<ChatConversation> conversations = chatExportParser.parseHtmlFile(file);
            ChatConversation targetConv = null;
            for (final ChatConversation conv : conversations) {
                if (Objects.equals(convId, conv.getConversationId())) {
                    targetConv = conv;
                    break;
                }
            }

            if (targetConv == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到指定 Conversation ID: " + convId
                ));
            }

            final List<ChatMessage> msgs = targetConv.getMessages();
            final VectorSegmentationResult result = vectorSessionSplitter.inspectConversation(targetConv);

            final Map<String, Object> convData = new HashMap<>();
            convData.put("id", targetConv.getConversationId());
            convData.put("title", targetConv.getTitle() != null ? targetConv.getTitle() : "未命名对话");
            convData.put("messagesCount", msgs.size());
            convData.put("sessionsCount", result.getSessions().size());

            final List<Map<String, Object>> pairList = new ArrayList<>();
            for (final ConversationPairDetail pair : result.getPairDetails()) {
                final Map<String, Object> p = new HashMap<>();
                p.put("index", pair.getIndex());
                p.put("roleA", pair.getRoleA());
                p.put("textA", pair.getTextA());
                p.put("roleB", pair.getRoleB());
                p.put("textB", pair.getTextB());
                p.put("score", pair.getScore());
                p.put("type", pair.getType());
                p.put("label", pair.getLabel());
                pairList.add(p);
            }
            convData.put("pairs", pairList);

            final List<Map<String, Object>> sessionList = new ArrayList<>();
            for (final ChatSession s : result.getSessions()) {
                final Map<String, Object> sData = new HashMap<>();
                sData.put("sessionId", s.getSessionId());
                sData.put("endTime", s.getEndTime() != null ? s.getEndTime().toString() : "");

                final List<Map<String, Object>> sessionMsgs = new ArrayList<>();
                if (s.getMessages() != null) {
                    for (final ChatMessage m : s.getMessages()) {
                        sessionMsgs.add(Map.of(
                                "role", m.getRole(),
                                "content", m.getContent() != null ? m.getContent() : ""
                        ));
                    }
                }
                sData.put("messages", sessionMsgs);
                sessionList.add(sData);
            }
            convData.put("sessions", sessionList);

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "获取详情成功",
                    "data", convData
            ));
        } catch (Exception e) {
            log.error("Failed to compute visualizer detail for convId: {}", convId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "计算切分详情失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取已完成核对的 Pair 历史列表（按时间倒序）
     *
     * @param limit 历史记录限制条数
     * @return 历史 Pair ResponseEntity
     */
    @GetMapping("/audited-history")
    @Operation(summary = "获取已完成核对的历史 Pair 列表", description = "用于人工回顾或二次反转修改已审核过的历史 Pair 记录")
    public ResponseEntity<Map<String, Object>> getAuditedHistory(
            @Parameter(description = "拉取条数", example = "50")
            @RequestParam(defaultValue = "50") final int limit) {
        try {
            final List<SessionBoundaryPairDO> historyList = sessionBoundaryPairMapper.selectAuditedHistory(limit);
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取已审核历史成功", "data", historyList));
        } catch (Exception e) {
            log.error("Failed to fetch audited history", e);
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "获取已审核历史失败: " + e.getMessage()));
        }
    }

    /**
     * 通用多阶段 (L1 / L2) 人工复核点击落库 REST 接口
     *
     * @param pairId  Pair 数据库自增 ID（也可为 parentConversationId + pairIndex）
     * @param stage   阶段编码 (L1 / L2)
     * @param status  核对状态 (PASSED / OVERRIDDEN / UNCHECKED)
     * @param verdict 判定结论 (MERGE / SPLIT)
     * @param remark  人工说明（可选）
     * @return 操作结果 ResponseEntity
     */
    @PostMapping("/audit")
    @Operation(summary = "提交人工复核裁决（支持 L1 / L2 多阶段与撤销重置）", description = "前端点击【通过】、【强改】或【撤销重置】按钮后，实时更新 session_boundary_pair 数据库")
    public ResponseEntity<Map<String, Object>> submitAudit(
            @Parameter(description = "Pair 记录 ID", example = "101")
            @RequestParam final Long pairId,
            @Parameter(description = "阶段 (L1 或 L2)", example = "L1")
            @RequestParam(defaultValue = "L1") final String stage,
            @Parameter(description = "核对状态 (PASSED, OVERRIDDEN 或 UNCHECKED)", example = "OVERRIDDEN")
            @RequestParam final String status,
            @Parameter(description = "人工决定 (MERGE 或 SPLIT)", example = "MERGE")
            @RequestParam final String verdict,
            @Parameter(description = "人工说明（可选）", example = "经人工核实为同话题续问")
            @RequestParam(required = false) final String remark) {

        if (pairId == null || status == null || verdict == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "缺少必填参数 pairId, status, verdict"));
        }

        try {
            int updated;
            final String trimmedStatus = status.trim();
            final String trimmedVerdict = verdict.trim();

            if ("UNCHECKED".equalsIgnoreCase(trimmedStatus)) {
                // 撤销重置：改回 PENDING 状态与 UNCHECKED 状态
                updated = sessionBoundaryPairMapper.updateL1AuditResult(pairId, "UNCHECKED", null, null, "撤销重置重入队列");
            } else if ("L2".equalsIgnoreCase(stage.trim())) {
                updated = sessionBoundaryPairMapper.updateL2AuditResult(pairId, trimmedStatus, trimmedVerdict, trimmedVerdict, remark);
            } else {
                updated = sessionBoundaryPairMapper.updateL1AuditResult(pairId, trimmedStatus, trimmedVerdict, trimmedVerdict, remark);
            }

            if (updated > 0) {
                log.info("[Audit] 成功落库人工复核 | pairId: {}, stage: {}, status: {}, verdict: {}", pairId, stage, trimmedStatus, trimmedVerdict);
                return ResponseEntity.ok(Map.of("code", 200, "message", "人工复核提交并落库成功"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("code", 404, "message", "未找到指定 pairId: " + pairId));
            }
        } catch (Exception e) {
            log.error("[Audit] 提交人工复核发生异常", e);
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "系统内部错误: " + e.getMessage()));
        }
    }

    /**
     * 条件分页获取 L2 大模型已审核落表记录列表
     */
    @GetMapping("/l2-records")
    @Operation(summary = "条件分页获取 L2 大模型精排裁决落表记录列表", description = "支持按附件有无、处理状态、裁决结论、置信度及关键字过滤检索")
    public ResponseEntity<Map<String, Object>> getL2Records(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int pageSize,
            @RequestParam(required = false) final Integer hasAttachment,
            @RequestParam(required = false) final String processStatus,
            @RequestParam(required = false) final String l2Verdict,
            @RequestParam(required = false) final String l2Confidence,
            @RequestParam(required = false) final String keyword) {

        final int safePage = Math.max(1, page);
        final int safePageSize = Math.min(100, Math.max(1, pageSize));
        final int offset = (safePage - 1) * safePageSize;

        final String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        final String cleanStatus = (processStatus != null && !processStatus.trim().isEmpty()) ? processStatus.trim() : null;
        final String cleanVerdict = (l2Verdict != null && !l2Verdict.trim().isEmpty()) ? l2Verdict.trim() : null;
        final String cleanConfidence = (l2Confidence != null && !l2Confidence.trim().isEmpty()) ? l2Confidence.trim() : null;

        final List<SessionBoundaryPairDO> list = sessionBoundaryPairMapper.selectL2Records(
                hasAttachment, cleanStatus, cleanVerdict, cleanConfidence, cleanKeyword, offset, safePageSize
        );
        final int total = sessionBoundaryPairMapper.countL2Records(
                hasAttachment, cleanStatus, cleanVerdict, cleanConfidence, cleanKeyword
        );

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取成功",
                "page", safePage,
                "pageSize", safePageSize,
                "total", total,
                "list", list
        ));
    }

    /**
     * 获取 L2 大模型裁决统计指标看板数据
     */
    @GetMapping("/l2-stats")
    @Operation(summary = "获取 L2 大模型精排裁决统计指标看板", description = "提供 FUZZY 模糊区总数、多模态图文数、纯文本数、已精排数、剩余未处理数、MERGE/SPLIT 与待复核数")
    public ResponseEntity<Map<String, Object>> getL2Stats() {
        final Map<String, Object> stats = sessionBoundaryPairMapper.selectL2SummaryStats();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取成功",
                "stats", stats != null ? stats : Map.of()
        ));
    }
}
