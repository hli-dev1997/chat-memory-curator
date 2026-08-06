package com.chatgpt.memory.integration.qwen;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 通义千问客户端集成测试类
 *
 * @author Antigravity
 */
@Slf4j
@SpringBootTest
public class QwenClientTest {

    @Autowired
    private QwenClient qwenClient;

    /**
     * 测试单轮 Prompt 文本生成功能
     */
    @Test
    @DisplayName("测试 Qwen 单轮对话生成功能")
    public void testGenerate() {
        String prompt = "你好";
        String response = qwenClient.generate(prompt);

        log.info("【Qwen 单轮对话测试】Prompt: '{}' -> Response: '{}'", prompt, response);

        assertNotNull(response, "响应不应为 null");
        assertFalse(response.isBlank(), "响应不应为空字符串");
    }

    /**
     * 测试带系统提示词的文本生成功能
     */
    @Test
    @DisplayName("测试 Qwen 带 System Prompt 对话生成功能")
    public void testGenerateWithSystem() {
        String systemPrompt = "你是一个Java技术专家。";
        String userPrompt = "请用一句话简述一下Java 21的虚拟线程特点。";
        String response = qwenClient.generateWithSystem(systemPrompt, userPrompt);

        log.info("【Qwen 系统提示词测试】System: '{}', User: '{}' -> Response: '{}'",
                systemPrompt, userPrompt, response);

        assertNotNull(response, "响应不应为 null");
        assertFalse(response.isBlank(), "响应不应为空字符串");
    }
}
