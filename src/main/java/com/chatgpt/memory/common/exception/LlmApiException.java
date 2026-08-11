package com.chatgpt.memory.common.exception;

/**
 * 类说明 / Class Description:
 * 中文：大模型 API 接口调用异常（如额度耗尽、Key失效、网络超频及4xx/5xx接口错误）。
 * English: Exception representing LLM API invocation failures (quota exhausted, invalid key, rate limits).
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：当底层大模型服务接口出现无法恢复的报错时抛出此异常，触发 Stage 2 阶段任务立即强行熔断中断，
 * 阻止伪降级与无效循环死置重试。
 * </p>
 *
 * @author Antigravity
 * @since 1.0.0
 */
public class LlmApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LlmApiException(final String message) {
        super(message);
    }

    public LlmApiException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
