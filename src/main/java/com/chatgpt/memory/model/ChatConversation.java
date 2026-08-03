package com.chatgpt.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

/**
 * 提取主线分支后的单场完整对话领域实体 POJO
 * <p>
 * 包含了从原始 Mapping 树图提纯出的按时间顺序排列的单条主线消息列表。
 * 提供 getFormattedFullText() 方法直接输出整场多轮对话的完整 Markdown 格式全文。
 * </p>
 *
 * @author Antigravity
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation {

    /**
     * 对话全局唯一标识 ID
     */
    private String conversationId;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 对话创建时间戳
     */
    private Instant createTime;

    /**
     * 主线采纳的有序消息列表
     */
    private List<ChatMessage> messages;

    /**
     * 将本场对话的所有主线消息拼接输出为整篇自然语言 Markdown 完整文本
     *
     * @return 完整的自然语言多轮对话全文
     */
    public String getFormattedFullText() {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title != null ? title : "Untitled Conversation").append("\n\n");

        for (final ChatMessage msg : messages) {
            final String roleName = "user".equalsIgnoreCase(msg.getRole()) ? "user" : "ChatGPT";
            builder.append("**").append(roleName).append("**\n");
            builder.append(msg.getContent()).append("\n\n");
        }
        return builder.toString().trim();
    }
}
