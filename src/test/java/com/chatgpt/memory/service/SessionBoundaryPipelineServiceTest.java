package com.chatgpt.memory.service;

import com.chatgpt.memory.common.enums.L1ZoneEnum;
import com.chatgpt.memory.common.enums.ProcessStatusEnum;
import com.chatgpt.memory.integration.qwen.QwenClient;
import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.ConversationPairDetail;
import com.chatgpt.memory.model.VectorSegmentationResult;
import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import com.chatgpt.memory.integration.qwen.QwenModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SessionBoundaryPipelineService 三阶段流水线单元测试类
 */
@ExtendWith(MockitoExtension.class)
class SessionBoundaryPipelineServiceTest {

    @Mock
    private SessionBoundaryPairMapper sessionBoundaryPairMapper;

    @Mock
    private VectorSessionSplitter vectorSessionSplitter;

    @Mock
    private QwenClient qwenClient;

    @Mock
    private QwenModelFactory qwenModelFactory;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SessionBoundaryPipelineService pipelineService;

    @Captor
    private ArgumentCaptor<List<SessionBoundaryPairDO>> pairListCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pipelineService, "highThreshold", 0.82);
        ReflectionTestUtils.setField(pipelineService, "lowThreshold", 0.60);
    }

    @Test
    @DisplayName("Stage 1: 边界计算与三区落库 (绿区>=0.82, 黄区0.60~0.82, 红区<0.60)")
    void testRunStage1_Success() {
        // Arrange
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId("conv-101");
        ChatMessage msg1 = new ChatMessage("msg1", "user", "如何使用 Redis？", Instant.now());
        ChatMessage msg2 = new ChatMessage("msg2", "assistant", "Redis 是一个高性能 Key-Value 数据库...", Instant.now());
        ChatMessage msg3 = new ChatMessage("msg3", "user", "顺便问问今天天气怎么样？", Instant.now());
        conversation.setMessages(List.of(msg1, msg2, msg3));

        ConversationPairDetail greenPair = new ConversationPairDetail(1, "user", "如何使用 Redis？", "assistant", "Redis 是一个高性能...", 0.88, "strong", "强相关");
        ConversationPairDetail fuzzyPair = new ConversationPairDetail(2, "assistant", "Redis 是一个...", "user", "内存优化技巧有哪些？", 0.72, "ambiguous", "模糊区");
        ConversationPairDetail redPair = new ConversationPairDetail(3, "user", "内存优化...", "user", "顺便问问今天天气？", 0.35, "split", "切断");

        VectorSegmentationResult mockResult = new VectorSegmentationResult(Collections.emptyList(), List.of(greenPair, fuzzyPair, redPair));
        when(vectorSessionSplitter.inspectConversation(conversation)).thenReturn(mockResult);
        when(sessionBoundaryPairMapper.batchInsert(any())).thenReturn(3);

        // Act
        int inserted = pipelineService.runStage1(List.of(conversation));

        // Assert
        assertThat(inserted).isEqualTo(3);
        verify(sessionBoundaryPairMapper, times(1)).batchInsert(pairListCaptor.capture());

        List<SessionBoundaryPairDO> insertedPairs = pairListCaptor.getValue();
        assertThat(insertedPairs).hasSize(3);

        // 验证 1: 绿区
        SessionBoundaryPairDO pair1 = insertedPairs.get(0);
        assertThat(pair1.getL1Zone()).isEqualTo(L1ZoneEnum.GREEN_MERGE.getCode());
        assertThat(pair1.getFinalDecision()).isEqualTo("MERGE");
        assertThat(pair1.getProcessStatus()).isEqualTo(ProcessStatusEnum.DONE.getCode());

        // 验证 2: 模糊黄区
        SessionBoundaryPairDO pair2 = insertedPairs.get(1);
        assertThat(pair2.getL1Zone()).isEqualTo(L1ZoneEnum.FUZZY.getCode());
        assertThat(pair2.getFinalDecision()).isNull();
        assertThat(pair2.getProcessStatus()).isEqualTo(ProcessStatusEnum.PENDING.getCode());

        // 验证 3: 红区
        SessionBoundaryPairDO pair3 = insertedPairs.get(2);
        assertThat(pair3.getL1Zone()).isEqualTo(L1ZoneEnum.RED_SPLIT.getCode());
        assertThat(pair3.getFinalDecision()).isEqualTo("SPLIT");
        assertThat(pair3.getProcessStatus()).isEqualTo(ProcessStatusEnum.DONE.getCode());
    }

    @Test
    @DisplayName("Stage 2: 扫描 PENDING 状态记录调用千问并成功更新结果")
    void testRunStage2_Success() {
        // Arrange
        SessionBoundaryPairDO pendingPair = SessionBoundaryPairDO.builder()
                .id(10L)
                .parentConversationId("conv-101")
                .pairIndex(2)
                .l1Score(new BigDecimal("0.7500"))
                .l1Zone(L1ZoneEnum.FUZZY.getCode())
                .messageAText("Redis 内存优化")
                .messageBText("内存被占满怎么办")
                .processStatus(ProcessStatusEnum.PENDING.getCode())
                .build();

        when(sessionBoundaryPairMapper.selectL2ProcessList(anyBoolean(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingPair))
                .thenReturn(Collections.emptyList());

        when(qwenModelFactory.getModel(any())).thenReturn(chatLanguageModel);
        String mockQwenJson = """
                {
                  "related": true,
                  "action": "MERGE",
                  "confidence": "HIGH",
                  "reason": "上文讨论 Redis 内存优化，下文追问内存满的异常处理"
                }
                """;
        when(chatLanguageModel.generate(any(), any(UserMessage.class))).thenReturn(Response.from(AiMessage.from(mockQwenJson)));

        // Act
        int processedCount = pipelineService.runStage2();

        // Assert
        assertThat(processedCount).isEqualTo(1);
        verify(sessionBoundaryPairMapper, times(1)).updateL2Result(
                eq(10L), eq("MERGE"), eq("HIGH"),
                eq("上文讨论 Redis 内存优化，下文追问内存满的异常处理"),
                eq("qwen3.6-flash-2026-04-16"),
                eq("MERGE"), eq(ProcessStatusEnum.DONE.getCode())
        );
    }

    @Test
    @DisplayName("Stage 2: 千问裁决置信度为 LOW 时降级为 MERGE 与 NEED_MANUAL_REVIEW")
    void testRunStage2_LowConfidence_FallbackToReview() {
        // Arrange
        SessionBoundaryPairDO pendingPair = SessionBoundaryPairDO.builder()
                .id(20L)
                .l1Score(new BigDecimal("0.6200"))
                .messageAText("Java 异常分析")
                .messageBText("这个框架怎么用")
                .processStatus(ProcessStatusEnum.PENDING.getCode())
                .build();

        when(sessionBoundaryPairMapper.selectL2ProcessList(anyBoolean(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingPair))
                .thenReturn(Collections.emptyList());

        when(qwenModelFactory.getModel(any())).thenReturn(chatLanguageModel);
        String lowConfidenceJson = """
                {
                  "related": false,
                  "action": "SPLIT",
                  "confidence": "LOW",
                  "reason": "信息较少，无法确定关联性"
                }
                """;
        when(chatLanguageModel.generate(any(), any(UserMessage.class))).thenReturn(Response.from(AiMessage.from(lowConfidenceJson)));

        // Act
        int processedCount = pipelineService.runStage2();

        // Assert
        assertThat(processedCount).isEqualTo(1);
        verify(sessionBoundaryPairMapper).updateL2Result(
                eq(20L), eq("SPLIT"), eq("LOW"),
                eq("信息较少，无法确定关联性"),
                eq("qwen3.6-flash-2026-04-16"),
                eq("MERGE"), eq(ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode())
        );
    }

    @Test
    @DisplayName("Stage 2: 千问响应 JSON 解析异常时按原则兜底 MERGE 与 NEED_MANUAL_REVIEW")
    void testRunStage2_JsonParseFailure_FallbackToReview() {
        // Arrange
        SessionBoundaryPairDO pendingPair = SessionBoundaryPairDO.builder()
                .id(30L)
                .l1Score(new BigDecimal("0.6800"))
                .messageAText("Segment A")
                .messageBText("Segment B")
                .processStatus(ProcessStatusEnum.PENDING.getCode())
                .build();

        when(sessionBoundaryPairMapper.selectL2ProcessList(anyBoolean(), anyLong(), anyInt()))
                .thenReturn(List.of(pendingPair))
                .thenReturn(Collections.emptyList());

        when(qwenModelFactory.getModel(any())).thenReturn(chatLanguageModel);
        when(chatLanguageModel.generate(any(), any(UserMessage.class))).thenReturn(Response.from(AiMessage.from("Invalid JSON text")));

        // Act
        int processedCount = pipelineService.runStage2();

        // Assert
        assertThat(processedCount).isEqualTo(1);
        verify(sessionBoundaryPairMapper).updateL2Result(
                eq(30L), eq("MERGE"), eq("LOW"),
                anyString(),
                eq("qwen3.6-flash-2026-04-16"),
                eq("MERGE"), eq(ProcessStatusEnum.NEED_MANUAL_REVIEW.getCode())
        );
    }

    @Test
    @DisplayName("Stage 3: 提取切分索引列表")
    void testGetSessionSplitIndices() {
        // Arrange
        SessionBoundaryPairDO pair1 = SessionBoundaryPairDO.builder().pairIndex(1).finalDecision("MERGE").build();
        SessionBoundaryPairDO pair2 = SessionBoundaryPairDO.builder().pairIndex(2).finalDecision("SPLIT").build();
        SessionBoundaryPairDO pair3 = SessionBoundaryPairDO.builder().pairIndex(3).finalDecision("MERGE").build();

        when(sessionBoundaryPairMapper.selectByConvId("conv-101")).thenReturn(List.of(pair1, pair2, pair3));

        // Act
        List<Integer> splitIndices = pipelineService.getSessionSplitIndices("conv-101");

        // Assert
        assertThat(splitIndices).containsExactly(2);
    }
}