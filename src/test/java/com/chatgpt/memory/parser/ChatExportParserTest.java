package com.chatgpt.memory.parser;

import com.chatgpt.memory.model.ChatConversation;
import com.chatgpt.memory.model.ChatMessage;
import com.chatgpt.memory.model.dto.RawConversationDto;
import com.chatgpt.memory.model.dto.RawMessageDto;
import com.chatgpt.memory.model.dto.RawNodeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatExportParserTest {

    private ChatExportParser parser;

    @BeforeEach
    void setUp() {
        parser = new ChatExportParser();
    }

    @Test
    @DisplayName("测试分支采纳与回溯算法：仅回溯 current_node 所在的主线分支，忽略放弃的分支")
    void testExtractMainlineMessages_BranchSelection() {
        final RawConversationDto rawConv = new RawConversationDto();
        rawConv.setId("conv-branch-1");
        rawConv.setTitle("Branch Test");
        rawConv.setCurrentNode("node3");

        final Map<String, RawNodeDto> mapping = new HashMap<>();

        // root node
        mapping.put("client-created-root", createNode("client-created-root", null, List.of("node1"), null));

        // node1 (User Q1)
        mapping.put("node1", createNode("node1", "client-created-root", List.of("node2_old", "node2_new"),
                createMessage("msg1", "user", "Question 1", 1000.0)));

        // node2_old (AI A1 - 废弃)
        mapping.put("node2_old", createNode("node2_old", "node1", List.of(),
                createMessage("msg2_old", "assistant", "Answer 1 (Abandoned)", 1005.0)));

        // node2_new (AI A2 - 采纳)
        mapping.put("node2_new", createNode("node2_new", "node1", List.of("node3"),
                createMessage("msg2_new", "assistant", "Answer 1 (Adopted)", 1006.0)));

        // node3 (User Q2 - current_node)
        mapping.put("node3", createNode("node3", "node2_new", List.of(),
                createMessage("msg3", "user", "Question 2", 1010.0)));

        rawConv.setMapping(mapping);

        final List<ChatMessage> messages = parser.extractMainlineMessages(rawConv);

        // 验证只提取主线 3 条消息，废弃的 msg2_old 不会被包含
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getContent()).isEqualTo("Question 1");
        assertThat(messages.get(1).getContent()).isEqualTo("Answer 1 (Adopted)");
        assertThat(messages.get(2).getContent()).isEqualTo("Question 2");
    }

    @Test
    @DisplayName("回归测试 1：验证 Dict Parts 字典结构（语音转写文本 audio_transcription）提取")
    void testExtractTextContent_DictPartsVoiceTranscription() {
        final RawConversationDto rawConv = new RawConversationDto();
        rawConv.setId("conv-dict-part");
        rawConv.setCurrentNode("node_user");

        final Map<String, RawNodeDto> mapping = new HashMap<>();
        mapping.put("client-created-root", createNode("client-created-root", null, List.of("node_user"), null));

        // 构造 parts 里包含 audio_transcription 字典的对象
        final RawMessageDto userMsg = new RawMessageDto();
        userMsg.setId("m_audio");
        final RawMessageDto.AuthorDto userAuthor = new RawMessageDto.AuthorDto();
        userAuthor.setRole("user");
        userMsg.setAuthor(userAuthor);
        final RawMessageDto.ContentDto userContent = new RawMessageDto.ContentDto();
        userContent.setParts(List.of(Map.of("content_type", "audio_transcription", "text", "语音转写测试文本")));
        userMsg.setContent(userContent);
        userMsg.setCreateTime(1700000000.0);

        mapping.put("node_user", createNode("node_user", "client-created-root", List.of(), userMsg));
        rawConv.setMapping(mapping);

        final List<ChatMessage> messages = parser.extractMainlineMessages(rawConv);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("语音转写测试文本");
    }

    @Test
    @DisplayName("回归测试 2：验证多级时间戳降级阶梯（Msg Time -> Conv Time -> Prev Msg Time -> Instant.now()）")
    void testExtractMainlineMessages_MultiLevelTimestampFallback() {
        // Conversation 时间戳为 1600000000
        final RawConversationDto rawConv = new RawConversationDto();
        rawConv.setId("conv-timestamp-fallback");
        rawConv.setCreateTime(1600000000.0);
        rawConv.setCurrentNode("node2");

        final Map<String, RawNodeDto> mapping = new HashMap<>();
        mapping.put("client-created-root", createNode("client-created-root", null, List.of("node1"), null));

        // node1: 消息无时间戳 (null) -> 降级继承 Conversation 时间戳 (1600000000)
        final RawMessageDto msg1 = createMessage("m1", "user", "Q1", null);

        // node2: 消息无时间戳 (null) -> 继承上一条消息的时间戳 (1600000000)
        final RawMessageDto msg2 = createMessage("m2", "assistant", "A1", null);

        mapping.put("node1", createNode("node1", "client-created-root", List.of("node2"), msg1));
        mapping.put("node2", createNode("node2", "node1", List.of(), msg2));
        rawConv.setMapping(mapping);

        final List<ChatMessage> messages = parser.extractMainlineMessages(rawConv);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getCreateTime()).isEqualTo(Instant.ofEpochMilli(1600000000000L));
        assertThat(messages.get(1).getCreateTime()).isEqualTo(Instant.ofEpochMilli(1600000000000L));

        // 测试全缺失场景（Conversation createTime 也为 null）
        rawConv.setCreateTime(null);
        final ChatConversation convObj = parser.parseSingleConversation(rawConv);
        assertThat(convObj.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("测试 Role 角色过滤：自动过滤 system 提示词与 tool 结果节点")
    void testExtractMainlineMessages_RoleFiltering() {
        final RawConversationDto rawConv = new RawConversationDto();
        rawConv.setId("conv-role-1");
        rawConv.setCurrentNode("node_assistant");

        final Map<String, RawNodeDto> mapping = new HashMap<>();
        mapping.put("client-created-root", createNode("client-created-root", null, List.of("node_sys"), null));
        mapping.put("node_sys", createNode("node_sys", "client-created-root", List.of("node_user"),
                createMessage("m1", "system", "System prompt context", 1000.0)));
        mapping.put("node_user", createNode("node_user", "node_sys", List.of("node_tool"),
                createMessage("m2", "user", "What is the status?", 1001.0)));
        mapping.put("node_tool", createNode("node_tool", "node_user", List.of("node_assistant"),
                createMessage("m3", "tool", "Tool call raw output", 1002.0)));
        mapping.put("node_assistant", createNode("node_assistant", "node_tool", List.of(),
                createMessage("m4", "assistant", "Status is healthy.", 1003.0)));

        rawConv.setMapping(mapping);

        final List<ChatMessage> messages = parser.extractMainlineMessages(rawConv);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
        assertThat(messages.get(0).getContent()).isEqualTo("What is the status?");
        assertThat(messages.get(1).getRole()).isEqualTo("assistant");
        assertThat(messages.get(1).getContent()).isEqualTo("Status is healthy.");
    }

    @Test
    @DisplayName("测试防御型防 NPE 处理：当 current_node 为 null 或缺失时跳过不报错")
    void testExtractMainlineMessages_DefensiveCheck() {
        final RawConversationDto rawConvNullNode = new RawConversationDto();
        rawConvNullNode.setId("conv-null-node");
        rawConvNullNode.setCurrentNode(null);

        final List<ChatMessage> res1 = parser.extractMainlineMessages(rawConvNullNode);
        assertThat(res1).isEmpty();

        final RawConversationDto rawConvMissingNode = new RawConversationDto();
        rawConvMissingNode.setId("conv-missing-node");
        rawConvMissingNode.setCurrentNode("non-existent-id");
        rawConvMissingNode.setMapping(Map.of("root", createNode("root", null, List.of(), null)));

        final List<ChatMessage> res2 = parser.extractMainlineMessages(rawConvMissingNode);
        assertThat(res2).isEmpty();
    }

    private RawNodeDto createNode(final String id, final String parent, final List<String> children, final RawMessageDto message) {
        final RawNodeDto node = new RawNodeDto();
        node.setId(id);
        node.setParent(parent);
        node.setChildren(children);
        node.setMessage(message);
        return node;
    }

    private RawMessageDto createMessage(final String id, final String role, final String text, final Double timestampSec) {
        final RawMessageDto msg = new RawMessageDto();
        msg.setId(id);

        final RawMessageDto.AuthorDto author = new RawMessageDto.AuthorDto();
        author.setRole(role);
        msg.setAuthor(author);

        final RawMessageDto.ContentDto content = new RawMessageDto.ContentDto();
        content.setParts(List.of(text));
        msg.setContent(content);

        msg.setCreateTime(timestampSec);
        return msg;
    }
}
