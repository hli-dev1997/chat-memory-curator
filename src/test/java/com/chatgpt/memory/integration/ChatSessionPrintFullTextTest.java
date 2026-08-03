package com.chatgpt.memory.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
public class ChatSessionPrintFullTextTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void findConversationsWithMostSessions() {
        final String sql = """
            SELECT 
                parent_conversation_id, 
                title, 
                COUNT(1) AS session_count, 
                SUM(message_count) AS total_messages 
            FROM chat_session 
            GROUP BY parent_conversation_id, title 
            HAVING COUNT(1) >= 3 
            ORDER BY session_count DESC 
            LIMIT 10;
        """;

        final List<Map<String, Object>> parentList = jdbcTemplate.queryForList(sql);

        log.info("\n====================================================================================");
        log.info("  被 4 小时规则切分成最多 Session 片段 (>= 3 个) 的长对话 TOP 10:");
        log.info("====================================================================================");

        for (Map<String, Object> parent : parentList) {
            final String parentId = (String) parent.get("parent_conversation_id");
            final String title = (String) parent.get("title");
            final Long count = (Long) parent.get("session_count");
            final Object totalMsgs = parent.get("total_messages");

            log.info("📌 对话标题: [{}] | 父 ID: [{}]", title, parentId);
            log.info("   共切分出 {} 个 Session 片段，总消息数: {}", count, totalMsgs);

            final List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                    "SELECT session_id, message_count, start_time, end_time FROM chat_session WHERE parent_conversation_id = ? ORDER BY start_time ASC",
                    parentId);

            for (Map<String, Object> s : sessions) {
                log.info("      ├─ Session ID: {} | 消息数: {} | 起止时间: {} ==> {}",
                        s.get("session_id"), s.get("message_count"), s.get("start_time"), s.get("end_time"));
            }
            log.info("------------------------------------------------------------------------------------");
        }
    }
}
