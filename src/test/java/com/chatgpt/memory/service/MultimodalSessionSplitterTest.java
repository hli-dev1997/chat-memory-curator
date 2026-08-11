package com.chatgpt.memory.service;

import com.chatgpt.memory.common.enums.LlmModelEnum;
import com.chatgpt.memory.common.enums.PromptTemplateEnum;
import com.chatgpt.memory.integration.qwen.QwenModelFactory;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 2 L2 图文多模态大模型 (qwen3.5-omni-flash) 真实端到端测试类
 * <p>
 * 测试提取本地真实导出的聊天图片、构造 Base64 多模态 UserMessage，并调用通义千问全模态模型返回决策 JSON。
 * 遵循单元测试 AIR 原则：自动化、独立性、可重复，使用断言进行自动校验。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@SpringBootTest
public class MultimodalSessionSplitterTest {

    @Autowired
    private QwenModelFactory qwenModelFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试含有真实图片的对话 Pair 进行 qwen3.5-omni-flash 全模态推导与裁决")
    public void testMultimodalPairInferenceWithRealImage() throws Exception {
        // 1. 模拟含真实图片引用的上下文片段 A 与 B
        final String textA = """
                小李，看你发来的截图，你的 Kafka 4.0 源码构建依赖在 C 盘。
                ![图片](/chat-assets/sediment://file_00000000acb87206b3c60401da796eea)
                请问你的 .gradle 隐藏目录具体在哪个位置？
                """;

        final String textB = """
                ![图片](/chat-assets/sediment://file_00000000acb87206b3c60401da796eea)
                哪里？小李，你这张图里本身就已经把答案“藏住了”，在 C:\\Users\\lihao\\.gradle。
                我们需要将 GRADLE_USER_HOME 迁移到 D 盘去。
                """;

        // 2. 提取本地磁盘文件并转码 Base64 DataURI
        final List<String> base64Images = ImageBase64Util.extractBase64Images(textA, textB);
        log.info("[MultimodalTest] 成功解析提取本地图片 Base64 条数: {}", base64Images.size());

        assertNotNull(base64Images, "提取的图片 Base64 列表不能为 null");
        assertFalse(base64Images.isEmpty(), "应当成功解析出至少 1 张本地文件 Base64 图片");
        assertTrue(base64Images.get(0).startsWith("data:image/"), "Base64 字符串必须符合 data:image/ Header 规范");

        // 3. 构建图文交错紧跟排布的 UserMessage 消息与 Prompt 模板
        final PromptTemplateEnum template = PromptTemplateEnum.L2_MULTIMODAL_SESSION_SPLIT;
        final List<Content> contents = new ArrayList<>();

        final String headerA = String.format("向量相似度得分：0.8500 (划归边界评估区)\n\n【对话片段 A (前文结尾约 200 字)】：\n%s", textA);
        contents.add(TextContent.from(headerA));
        final List<String> imagesA = ImageBase64Util.extractBase64Images(textA);
        for (final String uri : imagesA) {
            contents.add(ImageContent.from(uri));
        }

        final String headerB = String.format("\n\n【对话片段 B (当前上下文开头约 200 字)】：\n%s", textB);
        contents.add(TextContent.from(headerB));
        final List<String> imagesB = ImageBase64Util.extractBase64Images(textB);
        for (final String uri : imagesB) {
            contents.add(ImageContent.from(uri));
        }

        contents.add(TextContent.from("\n\n【裁决任务说明】：\n请结合上述紧跟文本顺序的 Chunk A 及其图片与 Chunk B 及其图片，判断 Chunk B 是否与 Chunk A 属于同一会话上下文，并按指定 JSON 格式输出判定结果。"));

        // 4. 调用通义千问全模态推荐模型 qwen3-omni-flash 实例
        final ChatLanguageModel omniModel = qwenModelFactory.getModel(LlmModelEnum.OMNI_QWEN3_FLASH);
        assertNotNull(omniModel, "获取的 qwen3-omni-flash 模型 Bean 不能为 null");

        log.info("[MultimodalTest] 开始发送多模态图文请求给 qwen3-omni-flash 模型...");
        final Response<AiMessage> response = omniModel.generate(
                SystemMessage.from(template.getSystemPrompt()),
                UserMessage.from(contents)
        );

        // 5. 校验模型返回响应结果
        assertNotNull(response, "模型 Response 不能为 null");
        assertNotNull(response.content(), "模型 AiMessage 不能为 null");

        final String rawResponse = response.content().text();
        log.info("[MultimodalTest] 真实全模态模型响应内容原文:\n{}", rawResponse);
        assertNotNull(rawResponse, "响应文本不能为 null");
        assertFalse(rawResponse.isBlank(), "模型响应内容不能为空字符串");

        // 6. 自动解析 JSON 字符串防爆断言
        String jsonText = rawResponse.trim();
        if (jsonText.startsWith("```")) {
            final int firstNewline = jsonText.indexOf('\n');
            final int lastBacktick = jsonText.lastIndexOf("```");
            if (firstNewline != -1 && lastBacktick > firstNewline) {
                jsonText = jsonText.substring(firstNewline + 1, lastBacktick).trim();
            }
        }

        final JsonNode jsonNode = objectMapper.readTree(jsonText);
        assertNotNull(jsonNode, "反序列化后的 JSON 节点不能为 null");
        assertTrue(jsonNode.has("action"), "JSON 必须包含 action 决定字段");
        assertTrue(jsonNode.has("confidence"), "JSON 必须包含 confidence 置信度字段");
        assertTrue(jsonNode.has("reason"), "JSON 必须包含 reason 推导原因字段");

        final String action = jsonNode.get("action").asText();
        final String confidence = jsonNode.get("confidence").asText();
        final String reason = jsonNode.get("reason").asText();

        log.info("[MultimodalTest] 测试通过！解析得出 action: {}, confidence: {}, reason: {}", action, confidence, reason);
        assertTrue("MERGE".equalsIgnoreCase(action) || "SPLIT".equalsIgnoreCase(action), "action 必须为 MERGE 或 SPLIT");
    }
}
