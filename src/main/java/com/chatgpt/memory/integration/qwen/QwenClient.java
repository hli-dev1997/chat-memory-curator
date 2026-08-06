package com.chatgpt.memory.integration.qwen;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 通义千问大模型集成客户端服务类
 * <p>
 * 封装与千问大模型的交互逻辑，提供单轮对话及带系统提示词的多角色文本生成能力。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenClient {

    private final ChatLanguageModel qwenChatLanguageModel;

    /**
     * 发送单轮用户 Prompt 对话请求
     *
     * @param prompt 用户提示词文本，不可为 null
     * @return 模型生成的响应字符串，非空保证
     */
    public String generate(String prompt) {
        Objects.requireNonNull(prompt, "prompt cannot be null");
        log.debug("Sending user prompt to Qwen: {}", prompt);

        Response<AiMessage> response = qwenChatLanguageModel.generate(UserMessage.from(prompt));
        if (response == null || response.content() == null) {
            log.warn("Received null response or content from Qwen for prompt: {}", prompt);
            return "";
        }

        String content = response.content().text();
        String result = content != null ? content : "";
        log.debug("Received response from Qwen: {}", result);
        return result;
    }

    /**
     * 发送包含 System Prompt 与 User Prompt 的多角色对话请求
     *
     * @param systemPrompt 系统提示词文本，不可为 null
     * @param userPrompt   用户提示词文本，不可为 null
     * @return 模型生成的响应字符串，非空保证
     */
    public String generateWithSystem(String systemPrompt, String userPrompt) {
        Objects.requireNonNull(systemPrompt, "systemPrompt cannot be null");
        Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
        log.debug("Sending system and user prompts to Qwen");

        Response<AiMessage> response = qwenChatLanguageModel.generate(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );
        if (response == null || response.content() == null) {
            log.warn("Received null response or content from Qwen for system and user prompts");
            return "";
        }

        String content = response.content().text();
        String result = content != null ? content : "";
        log.debug("Received response from Qwen: {}", result);
        return result;
    }
}
