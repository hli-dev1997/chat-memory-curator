package com.chatgpt.memory.parser;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.dto.RawConversationDto;
import com.chatgpt.memory.model.dto.RawMessageDto;
import com.chatgpt.memory.model.dto.RawNodeDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ChatGPT 导出 HTML 数据高效解析器
 * <p>
 * 核心优化：采用轻量级字符串定位 var jsonData = ，避开 Jsoup 87MB DOM 树构建以提升速度防 OOM。
 * 核心算法：基于 current_node 进行父节点链逆向回溯，提取实际采纳的主线对话分支，自动过滤废弃分支与 system/tool 噪音角色。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Component
public class ChatExportParser {

    /**
     * chat.html 中保存导出的 JSON 数据的 JavaScript 变量标记
     */
    private static final String JSON_MARKER = "var jsonData = ";

    /**
     * 根节点默认标识 ID
     */
    private static final String ROOT_NODE_ID = "client-created-root";

    /**
     * Jackson JSON 映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 默认保留的核心对话角色集合 (user, assistant)
     */
    private final Set<String> defaultRetainRoles;

    /**
     * 构造方法：初始化 Jackson 配置与标准保留角色
     */
    public ChatExportParser() {
        this.objectMapper = new ObjectMapper();
        // 忽略 JSON 中未定义的未知属性，保证极佳的前后兼容性
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.defaultRetainRoles = Set.of("user", "assistant");
    }

    /**
     * 从 chat.html 文件中高效解析所有的主线 Conversation
     *
     * @param htmlFile HTML 文件对象
     * @return 提取主线后的 Conversation 列表
     * @throws IOException 文件读取异常
     */
    public List<ChatConversation> parseHtmlFile(final File htmlFile) throws IOException {
        // 入参防御性非空校验
        Objects.requireNonNull(htmlFile, "htmlFile must not be null");
        if (!htmlFile.exists() || !htmlFile.isFile()) {
            throw new IllegalArgumentException("Target file does not exist: " + htmlFile.getAbsolutePath());
        }

        log.info("Starting fast parse on file: {}, size: {} bytes", htmlFile.getName(), htmlFile.length());

        // 步骤 1：流式定位提取 HTML 内的 JSON 字符串（避开 DOM 构建）
        final String jsonContent = extractJsonFromHtmlFile(htmlFile);
        if (jsonContent == null || jsonContent.isBlank()) {
            log.warn("No jsonData marker found in file: {}", htmlFile.getName());
            return Collections.emptyList();
        }

        // 步骤 2：使用 Jackson 反序列化为原始 DTO 列表
        final List<RawConversationDto> rawConversations = objectMapper.readValue(
                jsonContent,
                new TypeReference<List<RawConversationDto>>() {}
        );

        log.info("Successfully deserialized {} raw conversations", rawConversations.size());
        final List<ChatConversation> conversations = new ArrayList<>(rawConversations.size());

        // 步骤 3：逐条提取主线对话分支
        for (final RawConversationDto rawConv : rawConversations) {
            final ChatConversation conversation = parseSingleConversation(rawConv);
            if (conversation != null) {
                conversations.add(conversation);
            }
        }

        log.info("Successfully extracted {} conversations with mainline messages", conversations.size());
        return conversations;
    }

    /**
     * 将单个 RawConversationDto 转化为提纯后的领域模型 ChatConversation
     *
     * @param rawConv 原始 Conversation DTO
     * @return 转换后的 ChatConversation，若 rawConv 为空则返回 null
     */
    public ChatConversation parseSingleConversation(final RawConversationDto rawConv) {
        if (rawConv == null) {
            return null;
        }

        // 提取主线消息列表
        final List<ChatMessage> messages = extractMainlineMessages(rawConv);

        // 多级降级计算 Conversation 创建时间
        Instant createTime = parseTimestamp(rawConv.getCreateTime());
        if (createTime == null) {
            if (!messages.isEmpty()) {
                createTime = messages.get(0).getCreateTime();
            } else {
                log.warn("[TIMESTAMP_LEVEL4_FALLBACK] Conversation [{}] create_time and messages are both empty/null, defaulting to current time", rawConv.getId());
                createTime = Instant.now();
            }
        }

        final ChatConversation conv = new ChatConversation();
        conv.setConversationId(rawConv.getId());
        conv.setTitle(rawConv.getTitle() != null ? rawConv.getTitle() : "Untitled Conversation");
        conv.setCreateTime(createTime);
        conv.setMessages(messages);
        return conv;
    }

    /**
     * 核心算法：根据 current_node 逆向向上回溯提取主线采纳分支消息
     * <p>
     * 算法过程：
     * 1. 校验 current_node 与 mapping 映射是否存在。
     * 2. 从 current_node 开始，沿着 parent 指针循环向上寻找父节点，并加入 visitedNodeIds 防止脏数据循环链表。
     * 3. 将得到的节点路径翻转（Collections.reverse），恢复按时间推移的正序对话流。
     * 4. 遍历路径节点，清洗 system 注入提示词与 tool 结果节点，仅保留 user 和 assistant 角色。
     * 5. 提纯文本内容（兼容纯字符串与包含 text 字段的语音转写字典对象）。
     * 6. 执行四级时间戳降级阶梯（Msg Time -> Conv Time -> Prev Msg Time -> Instant.now()）。
     * </p>
     *
     * @param rawConv 原始对话对象
     * @return 按时间正序排序的主线 ChatMessage 列表
     */
    public List<ChatMessage> extractMainlineMessages(final RawConversationDto rawConv) {
        if (rawConv == null) {
            return Collections.emptyList();
        }

        final String convId = rawConv.getId();
        final String currentNodeId = rawConv.getCurrentNode();
        final Map<String, RawNodeDto> mapping = rawConv.getMapping();

        // 1. 防御性校验：current_node 为空或 mapping 为空，记录日志并优雅跳过
        if (currentNodeId == null || currentNodeId.isBlank()) {
            log.warn("Conversation [{}] current_node is null or blank, skipping message extraction", convId);
            return Collections.emptyList();
        }
        if (mapping == null || mapping.isEmpty()) {
            log.warn("Conversation [{}] mapping is empty, skipping message extraction", convId);
            return Collections.emptyList();
        }

        // 2. 防御性校验：current_node 在 mapping 中找不到（脏数据防御）
        if (!mapping.containsKey(currentNodeId)) {
            log.warn("Conversation [{}] current_node [{}] not found in mapping, skipping", convId, currentNodeId);
            return Collections.emptyList();
        }

        // 3. 从 current_node 开始向上逆向回溯
        final List<RawNodeDto> pathNodes = new ArrayList<>();
        final Set<String> visitedNodeIds = new HashSet<>();
        String currId = currentNodeId;

        while (currId != null && !currId.isBlank() && !ROOT_NODE_ID.equals(currId)) {
            // 防御环路死循环
            if (visitedNodeIds.contains(currId)) {
                log.warn("Conversation [{}] detected cycle loop at node [{}], stopping trace", convId, currId);
                break;
            }
            visitedNodeIds.add(currId);

            final RawNodeDto node = mapping.get(currId);
            if (node == null) {
                log.warn("Conversation [{}] broken pointer to missing parent node [{}]", convId, currId);
                break;
            }

            pathNodes.add(node);
            currId = node.getParent();
        }

        // 4. 将逆向路径翻转为正序（根节点 -> 活跃叶节点）
        Collections.reverse(pathNodes);

        // 5. 提取并过滤有效消息
        final List<ChatMessage> mainlineMessages = new ArrayList<>();
        final Instant fallbackConvCreateTime = parseTimestamp(rawConv.getCreateTime());

        for (final RawNodeDto node : pathNodes) {
            final RawMessageDto rawMsg = node.getMessage();
            if (rawMsg == null || rawMsg.getAuthor() == null) {
                continue;
            }

            final String role = rawMsg.getAuthor().getRole();
            if (role == null || !defaultRetainRoles.contains(role.toLowerCase())) {
                // 自动过滤 system 提示词与 tool 工具调用等非自然语言对话角色
                continue;
            }

            // 提纯文本内容
            final String textContent = extractTextContent(rawMsg.getContent());
            if (textContent == null || textContent.isBlank()) {
                continue;
            }

            // 四级时间戳降级阶梯计算
            Instant msgCreateTime = parseTimestamp(rawMsg.getCreateTime());
            if (msgCreateTime == null) {
                msgCreateTime = fallbackConvCreateTime; // Level 2: 使用 Conversation 的时间戳
            }
            if (msgCreateTime == null && !mainlineMessages.isEmpty()) {
                msgCreateTime = mainlineMessages.get(mainlineMessages.size() - 1).getCreateTime(); // Level 3: 继承上一条消息的时间戳
            }
            if (msgCreateTime == null) {
                log.warn("[TIMESTAMP_LEVEL4_FALLBACK] Message [{}] in Conversation [{}] triggered final Instant.now() fallback due to all timestamps being null", rawMsg.getId(), convId);
                msgCreateTime = Instant.now(); // Level 4: 系统当前时间兜底
            }

            final ChatMessage message = new ChatMessage();
            message.setMessageId(rawMsg.getId());
            message.setRole(role.toLowerCase());
            message.setContent(textContent);
            message.setCreateTime(msgCreateTime);
            mainlineMessages.add(message);
        }

        return mainlineMessages;
    }

    /**
     * 高效定位并提取 var jsonData = 后面的 JSON 字符串片段
     *
     * @param htmlFile HTML 文件句柄
     * @return 提取出的 JSON 字符串，未找到时返回 null
     * @throws IOException 文件读取异常
     */
    private String extractJsonFromHtmlFile(final File htmlFile) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new FileReader(htmlFile, StandardCharsets.UTF_8))) {
            final StringBuilder builder = new StringBuilder();
            String line;
            boolean foundMarker = false;

            while ((line = reader.readLine()) != null) {
                if (!foundMarker) {
                    final int index = line.indexOf(JSON_MARKER);
                    if (index != -1) {
                        foundMarker = true;
                        final String jsonStart = line.substring(index + JSON_MARKER.length());
                        builder.append(jsonStart).append("\n");
                    }
                } else {
                    if (line.contains("</script>")) {
                        final int scriptEndIdx = line.indexOf("</script>");
                        final String jsonEnd = line.substring(0, scriptEndIdx);
                        builder.append(jsonEnd);
                        break;
                    }
                    builder.append(line).append("\n");
                }
            }

            if (!foundMarker) {
                return null;
            }

            String result = builder.toString().trim();
            if (result.endsWith(";")) {
                result = result.substring(0, result.length() - 1).trim();
            }
            return result;
        }
    }

    /**
     * 从 ContentDto 中提取纯文本 parts
     * <p>
     * 兼顾兼容两种形态：
     * 1. 标准 String 字符串。
     * 2. 包含 text 字段的 Map 字典对象（如语音转写 audio_transcription 消息）。
     * </p>
     *
     * @param contentDto 原始 ContentDto 对象
     * @return 拼接好的纯文本，为空时返回 null
     */
    private String extractTextContent(final RawMessageDto.ContentDto contentDto) {
        if (contentDto == null || contentDto.getParts() == null || contentDto.getParts().isEmpty()) {
            return null;
        }

        final StringBuilder textBuilder = new StringBuilder();
        for (final Object part : contentDto.getParts()) {
            if (part instanceof String str && !str.isBlank()) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n");
                }
                textBuilder.append(str);
            } else if (part instanceof Map<?, ?> map) {
                // 提纯字典里的语音转写文本或图片 asset_pointer/file 链接
                final Object textObj = map.get("text");
                final Object assetPointer = map.get("asset_pointer");
                final Object fileId = map.get("file_id");

                if (textObj instanceof String str && !str.isBlank()) {
                    if (!textBuilder.isEmpty()) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(str);
                } else if (assetPointer instanceof String pointer && !pointer.isBlank()) {
                    if (!textBuilder.isEmpty()) {
                        textBuilder.append("\n");
                    }
                    final String url = formatAssetUrl(pointer);
                    textBuilder.append("![图片](").append(url).append(")");
                } else if (fileId instanceof String fid && !fid.isBlank()) {
                    if (!textBuilder.isEmpty()) {
                        textBuilder.append("\n");
                    }
                    final String url = formatAssetUrl(fid);
                    textBuilder.append("![图片](").append(url).append(")");
                }
            }
        }
        return textBuilder.toString();
    }

    /**
     * 将 ChatGPT 原始物理资产指针格式化为标准的 /chat-assets/ Web 路径
     */
    private String formatAssetUrl(final String rawPointer) {
        if (rawPointer == null || rawPointer.isBlank()) {
            return "";
        }
        String clean = rawPointer.trim();
        if (clean.contains("://")) {
            clean = clean.substring(clean.indexOf("://") + 3);
        }
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return "/chat-assets/" + clean;
    }

    /**
     * 将 Double Unix 时间戳（单位：秒）转为 Instant，无效时返回 null 供上层做优雅降级
     *
     * @param timestampSec Double 类型秒级时间戳
     * @return Instant 对象，非法或空值时返回 null
     */
    private Instant parseTimestamp(final Double timestampSec) {
        if (timestampSec == null || timestampSec.isNaN() || timestampSec <= 0) {
            return null;
        }
        final long epochMilli = (long) (timestampSec * 1000.0);
        return Instant.ofEpochMilli(epochMilli);
    }
}
