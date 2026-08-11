package com.chatgpt.memory.integration;

import com.chatgpt.memory.common.exception.LlmApiException;
import com.chatgpt.memory.service.SessionBoundaryPipelineService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 类说明 / Class Description:
 * 中文：Stage 2 L2 大模型调用异常熔断阻断机制自动化集成测试。
 * English: Automated integration test for Stage 2 L2 model API failure circuit breaker.
 * <p>
 * 遵守 AIR 与 BCDE 单元测试原则，验证当模型 API 发生不可恢复报错（如余额耗尽/Key失效）时，
 * 任务能够立即强行终止循环并抛出 LlmApiException 阻断，不再伪降级落库与无脑死循环重试。
 * </p>
 *
 * @author Antigravity
 * @since 1.0.0
 */
@SpringBootTest
public class L2CircuitBreakerTest {

    @Autowired
    private SessionBoundaryPipelineService sessionBoundaryPipelineService;

    @Test
    @DisplayName("测试大模型 API 异常强行熔断中断机制")
    public void testCircuitBreakerOnLlmApiException() {
        // 断言传入已耗尽免费额度的模型 (OMNI_QWEN_36_FLASH_2026_04_16) 触发 LlmApiException 阻断
        Assertions.assertThrows(LlmApiException.class, () -> {
            sessionBoundaryPipelineService.runStage2(com.chatgpt.memory.common.enums.LlmModelEnum.OMNI_QWEN35_FLASH, null, false, 5);
        }, "当底层大模型 API 接口调用异常或传入配额耗尽模型时，必须强制抛出 LlmApiException 并阻断任务，决不能静默降级继续处理下一个记录");
    }
}
