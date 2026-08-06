package com.chatgpt.memory.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 通义千问 (Qwen) ChatLanguageModel Spring 上下文配置类
 * <p>
 * 将 OpenAI 兼容模式下的通义千问模型注册为 Spring 容器管理的单例 Bean。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class QwenConfig {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final QwenProperties qwenProperties;

    /**
     * 注册 Qwen ChatLanguageModel 单例 Bean
     *
     * @return OpenAI 兼容模式的 ChatLanguageModel 实例
     */
    @Bean
    public ChatLanguageModel qwenChatLanguageModel() {
        log.info("Initializing Qwen ChatLanguageModel with baseUrl: {}, modelName: {}",
                qwenProperties.getBaseUrl(), qwenProperties.getModelName());

        int timeout = qwenProperties.getTimeoutSeconds() != null
                ? qwenProperties.getTimeoutSeconds()
                : DEFAULT_TIMEOUT_SECONDS;
        boolean enableLogRequests = Boolean.TRUE.equals(qwenProperties.getLogRequests());
        boolean enableLogResponses = Boolean.TRUE.equals(qwenProperties.getLogResponses());

        return OpenAiChatModel.builder()
                .baseUrl(qwenProperties.getBaseUrl())
                .apiKey(qwenProperties.getApiKey())
                .modelName(qwenProperties.getModelName())
                .timeout(Duration.ofSeconds(timeout))
                .logRequests(enableLogRequests)
                .logResponses(enableLogResponses)
                .build();
    }
}
