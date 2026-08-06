package com.chatgpt.memory.service;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.ChatSession;
import com.chatgpt.memory.model.ConversationPairDetail;
import com.chatgpt.memory.model.VectorSegmentationResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于 BGE 中文向量余弦相似度的 L1 语义对话切分服务实现
 * <p>
 * 采用 L1 向量初筛与双门限（默认为 0.78 与 0.70）判定，结合短文本噪声上下文补全，
 * 将完整的 ChatConversation 动态分割为语义聚焦的 ChatSession 记忆片段。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@Service
public class VectorSessionSplitter {

    /**
     * BGE 向量模型单例 Bean
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 强相关直连门限（>= highThreshold 直接合并归入同一 Session）
     */
    private final double highThreshold;

    /**
     * 跨主题切断门限 (< lowThreshold 强制打断开启新 Session)
     */
    private final double lowThreshold;

    private final int shortTextMinLength;

    public VectorSessionSplitter(
            final EmbeddingModel embeddingModel,
            @Value("${chat.segmentation.l1-high-threshold:0.82}") final double highThreshold,
            @Value("${chat.segmentation.l1-low-threshold:0.60}") final double lowThreshold,
            @Value("${chat.segmentation.short-text-min-length:30}") final int shortTextMinLength) {

        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        this.highThreshold = highThreshold;
        this.lowThreshold = lowThreshold;
        this.shortTextMinLength = shortTextMinLength;

        log.info("Initialized VectorSessionSplitter with HighThreshold: {}, LowThreshold: {}, ShortTextMinLength: {}",
                highThreshold, lowThreshold, shortTextMinLength);
    }

    /**
     * 对全量对话列表按 BGE 向量语义进行切分
     *
     * @param conversations 全量对话列表
     * @return 切分后的 Session 列表
     */
    public List<ChatSession> splitConversations(final List<ChatConversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyList();
        }

        final List<ChatSession> result = new ArrayList<>();
        for (final ChatConversation conv : conversations) {
            result.addAll(splitConversation(conv));
        }

        log.info("Successfully split {} conversations into {} vector-segmented sessions",
                conversations.size(), result.size());
        return result;
    }

    /**
     * 对单个 Conversation 按 BGE 向量语义余弦相似度进行物理切片
     *
     * @param conversation 对话实体
     * @return 语义聚焦的 Session 列表
     */
    public List<ChatSession> splitConversation(final ChatConversation conversation) {
        final VectorSegmentationResult result = inspectConversation(conversation);
        return result != null ? result.getSessions() : Collections.emptyList();
    }

    /**
     * 对单个 Conversation 进行 BGE 向量切分并同时返回逐句 Pair 余弦得分与判定 Tag 明细
     *
     * @param conversation 对话实体
     * @return 包含切分 Session 列表与 Pair 得分明细列表的聚合结果
     */
    public VectorSegmentationResult inspectConversation(final ChatConversation conversation) {
        if (conversation == null || conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            return new VectorSegmentationResult(Collections.emptyList(), Collections.emptyList());
        }

        final List<ChatMessage> messages = conversation.getMessages();
        final String convId = conversation.getConversationId();
        final List<ChatSession> sessions = new ArrayList<>();
        final List<ConversationPairDetail> pairDetails = new ArrayList<>();

        List<ChatMessage> currentSessionMsgs = new ArrayList<>();
        Embedding baseEmbedding = null;
        Instant startTime = null;
        Instant endTime = null;
        int sessionIndex = 1;
        int pairIndex = 1;

        ChatMessage prevMessage = null;

        for (final ChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }

            final Instant msgTime = message.getCreateTime() != null ? message.getCreateTime() : Instant.now();

            if (currentSessionMsgs.isEmpty()) {
                // 当前片段首条消息，计算基准 Embedding
                currentSessionMsgs.add(message);
                baseEmbedding = computeSafeEmbedding(message.getContent(), null);
                startTime = msgTime;
                endTime = msgTime;
            } else {
                // 计算滑动窗口上下文（向前结合 1~2 条消息作为 Context）
                final String windowContext = getSlidingWindowContext(currentSessionMsgs, 2);
                final Embedding targetEmbedding = computeSafeEmbedding(message.getContent(), windowContext);

                final double score = (baseEmbedding != null && targetEmbedding != null)
                        ? CosineSimilarity.between(baseEmbedding, targetEmbedding)
                        : 0.0;

                final double roundedScore = Math.round(score * 10000.0) / 10000.0;
                String type;
                String label;
                if (score >= highThreshold) {
                    type = "strong";
                    label = String.format("🟢 绝对强相关 (Score >= %.2f)", highThreshold);
                } else if (score >= lowThreshold) {
                    type = "ambiguous";
                    label = String.format("🟡 划归模糊延伸区 (%.2f <= Score < %.2f)", lowThreshold, highThreshold);
                } else {
                    type = "split";
                    label = String.format("🔴 跨主题强制切断 (Score < %.2f)", lowThreshold);
                }

                if (prevMessage != null) {
                    pairDetails.add(new ConversationPairDetail(
                            pairIndex++,
                            prevMessage.getRole(),
                            prevMessage.getContent(),
                            message.getRole(),
                            message.getContent(),
                            roundedScore,
                            type,
                            label
                    ));
                }

                log.info("  -> Msg [{}]: \"{}\" | Score: {} | LowThreshold: {}",
                        message.getRole(), truncateText(message.getContent(), 40), String.format("%.4f", score), lowThreshold);

                if (score < lowThreshold) {
                    log.info("  ==> [CUT TRIGGERED] Score {} < {} | Start new Session #{}",
                            String.format("%.4f", score), lowThreshold, sessionIndex + 1);
                    // 跨主题打断，生成新 Session
                    final ChatSession session = createSession(convId, sessionIndex++, startTime, endTime, currentSessionMsgs);
                    sessions.add(session);

                    currentSessionMsgs = new ArrayList<>();
                    currentSessionMsgs.add(message);
                    baseEmbedding = targetEmbedding;
                    startTime = msgTime;
                } else {
                    // 归入当前 Session，并更新基准向量
                    currentSessionMsgs.add(message);
                    baseEmbedding = targetEmbedding;
                }
                endTime = msgTime;
            }
            prevMessage = message;
        }

        if (!currentSessionMsgs.isEmpty()) {
            final ChatSession lastSession = createSession(convId, sessionIndex, startTime, endTime, currentSessionMsgs);
            sessions.add(lastSession);
        }

        return new VectorSegmentationResult(sessions, pairDetails);
    }

    /**
     * 提取滑动窗口上下文字符串（向前结合最近 windowSize 条消息）
     */
    private String getSlidingWindowContext(final List<ChatMessage> messages, final int windowSize) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        final int start = Math.max(0, messages.size() - windowSize);
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            final String content = messages.get(i).getContent();
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(content.trim());
            }
        }
        return sb.length() > 200 ? sb.substring(sb.length() - 200) : sb.toString();
    }

    /**
     * 安全计算消息 Embedding，包含短文本补全与 350 字截断
     */
    private Embedding computeSafeEmbedding(final String rawText, final String previousContext) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String effectiveText = rawText.trim();
        // 短文本噪声抑制：若文本过短（如“可以吧”），拼接上文 Chunk
        if (effectiveText.length() < shortTextMinLength && previousContext != null && !previousContext.isBlank()) {
            final String contextSnippet = previousContext.substring(0, Math.min(previousContext.length(), 100));
            effectiveText = contextSnippet + " " + effectiveText;
        }

        if (effectiveText.length() <= 350) {
            try {
                return embeddingModel.embed(effectiveText).content();
            } catch (Exception e) {
                log.warn("Failed to compute embedding for text: {}, error: {}", effectiveText, e.getMessage());
                return null;
            }
        }

        // 超长文本分批切片计算（每 350 字一个 Chunk，求 512 维均值向量 Mean Pooling）
        final List<float[]> vectorList = new ArrayList<>();
        for (int i = 0; i < effectiveText.length(); i += 350) {
            final String chunk = effectiveText.substring(i, Math.min(effectiveText.length(), i + 350));
            try {
                final Embedding emb = embeddingModel.embed(chunk).content();
                if (emb != null && emb.vector() != null) {
                    vectorList.add(emb.vector());
                }
            } catch (Exception e) {
                log.warn("Failed to compute chunk embedding: {}, error: {}", chunk, e.getMessage());
            }
        }

        if (vectorList.isEmpty()) {
            return null;
        }

        final int dim = vectorList.get(0).length;
        final float[] meanVector = new float[dim];
        for (final float[] vec : vectorList) {
            for (int d = 0; d < dim; d++) {
                meanVector[d] += vec[d];
            }
        }
        for (int d = 0; d < dim; d++) {
            meanVector[d] /= vectorList.size();
        }

        return Embedding.from(meanVector);
    }

    /**
     * 构建 ChatSession 片段实体
     */
    private ChatSession createSession(
            final String convId,
            final int index,
            final Instant startTime,
            final Instant endTime,
            final List<ChatMessage> messages) {

        final String sessionId = Objects.requireNonNullElse(convId, "conv") + "-vec-s" + index;
        final ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setParentConversationId(convId);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setMessages(new ArrayList<>(messages));
        return session;
    }

    private static String truncateText(final String text, final int maxLength) {
        if (text == null) return "";
        final String s = text.trim();
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }
}
