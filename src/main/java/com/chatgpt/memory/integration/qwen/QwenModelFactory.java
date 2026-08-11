package com.chatgpt.memory.integration.qwen;

import com.chatgpt.memory.common.enums.LlmModelEnum;
import com.chatgpt.memory.config.QwenProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通义千问模型客户端动态构建与缓存工厂
 * <p>
 * 统一根据 {@link LlmModelEnum} 获取或按需创建对应的 ChatLanguageModel 实例，
 * 共享系统配置的 baseUrl 与 apiKey，避免硬编码或配置多个冗余 Bean。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenModelFactory {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final QwenProperties qwenProperties;
    private final Map<LlmModelEnum, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 根据模型枚举获取或动态创建对应的 ChatLanguageModel 实例
     *
     * @param modelEnum 模型枚举项，不可为 null
     * @return ChatLanguageModel 实例
     */
    public ChatLanguageModel getModel(final LlmModelEnum modelEnum) {
        Objects.requireNonNull(modelEnum, "modelEnum 不能为 null");

        if (modelEnum.isQuotaExhausted()) {
            log.error("[QwenModelFactory] 尝试构建已耗尽免费额度的模型 [{}] 被系统主动阻断拦截。", modelEnum.getModelName());
            throw new com.chatgpt.memory.common.exception.LlmApiException(
                    "模型 [" + modelEnum.getModelName() + "] 免费额度已用尽，系统已主动阻断，请切换使用最佳平替模型 qwen3.6-flash-2026-04-16！");
        }

        return modelCache.computeIfAbsent(modelEnum, enumKey -> {
            final String modelName = enumKey.getModelName();
            final int timeout = qwenProperties.getTimeoutSeconds() != null
                    ? qwenProperties.getTimeoutSeconds()
                    : DEFAULT_TIMEOUT_SECONDS;
            final boolean enableLogRequests = Boolean.TRUE.equals(qwenProperties.getLogRequests());
            final boolean enableLogResponses = Boolean.TRUE.equals(qwenProperties.getLogResponses());

            log.info("[QwenModelFactory] 动态构建 OpenAiChatModel 实例 | 模型: {}, 类型: {}",
                    modelName, enumKey.getModelType());

            return OpenAiChatModel.builder()
                    .baseUrl(qwenProperties.getBaseUrl())
                    .apiKey(qwenProperties.getApiKey())
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(timeout))
                    .logRequests(enableLogRequests)
                    .logResponses(enableLogResponses)
                    .build();
        });
    }
}
