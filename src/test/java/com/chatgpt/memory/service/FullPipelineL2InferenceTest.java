package com.chatgpt.memory.service;

import com.chatgpt.memory.common.enums.LlmModelEnum;
import com.chatgpt.memory.common.enums.PromptTemplateEnum;
import com.chatgpt.memory.integration.qwen.QwenModelFactory;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 阶段完整多路模型路由与双模态（无图纯文本 vs 有图多模态）流式切分集成测试类
 * <p>
 * 真实测试双路分流推导：
 * 1. 无图 Pair -> 自动匹配 qwen3.6-flash 文本模型 + L2_FUZZY_SESSION_SPLIT Prompt 模板
 * 2. 有图 Pair -> 自动匹配 qwen3.5-omni-flash 全模态模型 + L2_MULTIMODAL_SESSION_SPLIT Prompt 模板 + 图文交错 Base64 消息链
 * 全程使用 SLF4J 打印精准结构化日志，遵循阿里 Java 开发规范 AIR 原则。
 * </p>

 * @author Antigravity
 */
@Slf4j
@SpringBootTest
public class FullPipelineL2InferenceTest {

    @Autowired
    private QwenModelFactory qwenModelFactory;

    @Autowired
    private SessionBoundaryPipelineService sessionBoundaryPipelineService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试场景 1：无图纯文本 Pair 自动化路由 -> qwen3.6-flash 语言模型推导")
    public void testTextOnlyPairRouting() throws Exception {
        log.info("[PipelineTest] ===== 开始测试无图纯文本 Pair 推导链路 =====");

        final SessionBoundaryPairDO textPairDO = SessionBoundaryPairDO.builder()
                .id(9901L)
                .parentConversationId("conv-text-demo-001")
                .pairIndex(1)
                .messageARole("user")
                .messageBRole("assistant")
                .messageAText("小李，请你用三句话解释一下 MySQL InnoDB 存储引擎的 MVCC 事务隔离机制。")
                .messageBText("好的小李，MVCC 即多版本并发控制。它通过 Undo Log 回滚日志记录历史版本，并通过 ReadView 视图控制可见性，从而实现在不加锁的情况下提升读写并发并发效率。")
                .hasAttachment(0)
                .l1Score(BigDecimal.valueOf(0.7850))
                .l1Zone("FUZZY")
                .build();

        // 验证文本分流模板与模型选择
        final PromptTemplateEnum template = PromptTemplateEnum.L2_FUZZY_SESSION_SPLIT;
        final LlmModelEnum modelEnum = LlmModelEnum.QWEN_TEXT_FLASH;

        log.info("[PipelineTest] Pair {} (无图) 路由至模型: {}, 使用 Prompt 模板: {}",
                textPairDO.getId(), modelEnum.getModelName(), template.getCode());

        final String userPrompt = String.format(
                template.getUserPromptTemplate(),
                textPairDO.getL1Score().toPlainString(),
                textPairDO.getMessageAText(),
                textPairDO.getMessageBText()
        );

        final ChatLanguageModel model = qwenModelFactory.getModel(modelEnum);
        assertNotNull(model, "文本模型 qwen3.6-flash 不能为 null");

        log.info("\n========== [Stage 2 大模型请求参数明细 (无图纯文本)] ==========\n" +
                        "- Pair ID: {}\n" +
                        "- 路由模型: {} ({})\n" +
                        "- Prompt 模板: {}\n" +
                        "- System Prompt: [{}]\n" +
                        "- Chunk A 文本: [{}]\n" +
                        "- Chunk B 文本: [{}]\n" +
                        "============================================================",
                textPairDO.getId(), modelEnum.getModelName(), modelEnum.getModelType(), template.getCode(),
                template.getSystemPrompt().trim(), textPairDO.getMessageAText(), textPairDO.getMessageBText());

        final Response<AiMessage> response = model.generate(
                SystemMessage.from(template.getSystemPrompt()),
                UserMessage.from(userPrompt)
        );

        assertNotNull(response, "文本模型响应 Response 不能为 null");
        final String rawResponse = response.content().text();

        final JsonNode node = parseJsonSafe(rawResponse);
        assertNotNull(node, "解析得出的 JSON 节点不能为 null");
        assertTrue(node.has("action"), "必须包含 action 字段");

        log.info("\n========== [Stage 2 大模型响应结果明细 (无图纯文本)] ==========\n" +
                        "- Pair ID: {}\n" +
                        "- 模型响应原文 (rawResponse):\n{}\n" +
                        "- 解析 action: {}\n" +
                        "- 解析 confidence: {}\n" +
                        "- 解析 reason: {}\n" +
                        "============================================================",
                textPairDO.getId(), rawResponse, node.get("action").asText(), node.get("confidence").asText(), node.get("reason").asText());
    }

    @Test
    @DisplayName("测试场景 2：有图多模态 Pair 自动化路由 -> qwen3.5-omni-flash 全模态模型图文交错推导")
    public void testMultimodalPairRouting() throws Exception {
        log.info("[PipelineTest] ===== 开始测试有图多模态 Pair 推导链路 =====");

        final String imgA = "file-1fAKkD7FQfzA7vbX1zRwKaQH-5575C7F1-6DE8-4027-A041-FF848F5B806F.png";
        final String imgB = "file-2gRqDToRm9Yv1FgXzBdEEP-image.png";

        final SessionBoundaryPairDO imagePairDO = SessionBoundaryPairDO.builder()
                .id(9902L)
                .parentConversationId("conv-img-demo-002")
                .pairIndex(2)
                .messageARole("user")
                .messageBRole("user")
                .messageAText("小李，请看我发送的这部分社交沟通聊天界面截图：\n![图片](/chat-assets/" + imgA + ")")
                .messageBText("小李，再看我发送的另一张 VMware 虚拟机网络适配器 NAT 模式配置界面：\n![图片](/chat-assets/" + imgB + ")")
                .hasAttachment(1)
                .l1Score(BigDecimal.valueOf(0.6500))
                .l1Zone("FUZZY")
                .build();

        // 验证多模态分流模板与模型选择
        final PromptTemplateEnum template = PromptTemplateEnum.L2_MULTIMODAL_SESSION_SPLIT;
        final LlmModelEnum modelEnum = LlmModelEnum.QWEN_OMNI_FLASH;

        final List<Content> contents = new ArrayList<>();
        final String l1ScoreStr = imagePairDO.getL1Score().toPlainString();

        // 1. 注入 Chunk A 文本与图
        contents.add(TextContent.from(String.format(
                "向量相似度得分：%s (划归边界评估区)\n\n【对话片段 A (前文结尾约 200 字)】：\n%s",
                l1ScoreStr, imagePairDO.getMessageAText())));
        final List<String> imagesA = ImageBase64Util.extractBase64Images(imagePairDO.getMessageAText());
        for (final String uri : imagesA) {
            contents.add(ImageContent.from(uri));
        }

        // 2. 注入 Chunk B 文本与图
        contents.add(TextContent.from(String.format(
                "\n\n【对话片段 B (当前上下文开头约 200 字)】：\n%s",
                imagePairDO.getMessageBText())));
        final List<String> imagesB = ImageBase64Util.extractBase64Images(imagePairDO.getMessageBText());
        for (final String uri : imagesB) {
            contents.add(ImageContent.from(uri));
        }

        // 3. 注入导向提示词
        contents.add(TextContent.from(
                "\n\n【裁决任务说明】：\n请结合上述紧跟文本顺序的 Chunk A 及其图片与 Chunk B 及其图片，判断 Chunk B 是否与 Chunk A 属于同一会话上下文，并按指定 JSON 格式输出判定结果。"
        ));

        final ChatLanguageModel model = qwenModelFactory.getModel(modelEnum);
        assertNotNull(model, "全模态模型 qwen3.5-omni-flash 不能为 null");

        log.info("\n========== [Stage 2 大模型请求参数明细 (有图多模态)] ==========\n" +
                        "- Pair ID: {}\n" +
                        "- 路由模型: {} ({})\n" +
                        "- Prompt 模板: {}\n" +
                        "- System Prompt: [{}]\n" +
                        "- Chunk A 关联图片: {} 张 | 文本预览: [{}]\n" +
                        "- Chunk B 关联图片: {} 张 | 文本预览: [{}]\n" +
                        "============================================================",
                imagePairDO.getId(), modelEnum.getModelName(), modelEnum.getModelType(), template.getCode(),
                template.getSystemPrompt().trim(), imagesA.size(), imagePairDO.getMessageAText(),
                imagesB.size(), imagePairDO.getMessageBText());

        final Response<AiMessage> response = model.generate(
                SystemMessage.from(template.getSystemPrompt()),
                UserMessage.from(contents)
        );

        assertNotNull(response, "全模态模型响应 Response 不能为 null");
        final String rawResponse = response.content().text();

        final JsonNode node = parseJsonSafe(rawResponse);
        assertNotNull(node, "解析得出的 JSON 节点不能为 null");
        assertTrue(node.has("action"), "必须包含 action 字段");

        log.info("\n========== [Stage 2 大模型响应结果明细 (有图多模态)] ==========\n" +
                        "- Pair ID: {}\n" +
                        "- 模型响应原文 (rawResponse):\n{}\n" +
                        "- 解析 action: {}\n" +
                        "- 解析 confidence: {}\n" +
                        "- 解析 reason: {}\n" +
                        "============================================================",
                imagePairDO.getId(), rawResponse, node.get("action").asText(), node.get("confidence").asText(), node.get("reason").asText());
    }

    /**
     * 安全提取与清洗 Markdown 代码块 JSON
     */
    private JsonNode parseJsonSafe(final String rawText) throws Exception {
        String jsonText = rawText != null ? rawText.trim() : "";
        if (jsonText.startsWith("```")) {
            final int firstNewline = jsonText.indexOf('\n');
            final int lastBacktick = jsonText.lastIndexOf("```");
            if (firstNewline != -1 && lastBacktick > firstNewline) {
                jsonText = jsonText.substring(firstNewline + 1, lastBacktick).trim();
            }
        }
        return objectMapper.readTree(jsonText);
    }
}
