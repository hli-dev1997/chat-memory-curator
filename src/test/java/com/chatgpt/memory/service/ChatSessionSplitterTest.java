package com.chatgpt.memory.service;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionSplitterTest {

    private ChatSessionSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new ChatSessionSplitter(4L);
    }

    @Test
    @DisplayName("测试会话切分时间阈值边界：1h 与 3.5h 在同 Session，4.1h 与 12h 触发切分为新 Session")
    void testSplitConversation_TimeBoundaries() {
        final Instant baseTime = Instant.parse("2025-12-01T10:00:00Z");

        final ChatMessage msg1 = new ChatMessage("m1", "user", "Question 1", baseTime);
        final ChatMessage msg2 = new ChatMessage("m2", "assistant", "Answer 1", baseTime.plus(1, ChronoUnit.HOURS));
        final ChatMessage msg3 = new ChatMessage("m3", "user", "Question 2", baseTime.plus(4, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES));
        final ChatMessage msg4 = new ChatMessage("m4", "assistant", "Answer 2", baseTime.plus(8, ChronoUnit.HOURS).plus(36, ChronoUnit.MINUTES));
        final ChatMessage msg5 = new ChatMessage("m5", "user", "Question 3", baseTime.plus(20, ChronoUnit.HOURS).plus(36, ChronoUnit.MINUTES));

        final ChatConversation conversation = new ChatConversation(
                "conv-time-test",
                "Time Boundary Test",
                baseTime,
                List.of(msg1, msg2, msg3, msg4, msg5)
        );

        final List<ChatSession> sessions = splitter.splitConversation(conversation);

        assertThat(sessions).hasSize(3);

        assertThat(sessions.get(0).getSessionId()).isEqualTo("conv-time-test-s1");
        assertThat(sessions.get(0).getMessages()).containsExactly(msg1, msg2, msg3);

        assertThat(sessions.get(1).getSessionId()).isEqualTo("conv-time-test-s2");
        assertThat(sessions.get(1).getMessages()).containsExactly(msg4);

        assertThat(sessions.get(2).getSessionId()).isEqualTo("conv-time-test-s3");
        assertThat(sessions.get(2).getMessages()).containsExactly(msg5);
    }

    @Test
    @DisplayName("测试防御性边界：空消息列表返回空 Session 列表")
    void testSplitConversation_EmptyMessages() {
        final ChatConversation emptyConv = new ChatConversation("conv-empty", "Empty", Instant.now(), List.of());
        final List<ChatSession> result = splitter.splitConversation(emptyConv);
        assertThat(result).isEmpty();
    }
}
