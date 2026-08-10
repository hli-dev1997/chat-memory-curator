package com.chatgpt.memory;

import com.chatgpt.memory.mapper.SessionBoundaryPairMapper;
import com.chatgpt.memory.model.entity.SessionBoundaryPairDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest
public class AuditSchemaUpdateTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionBoundaryPairMapper sessionBoundaryPairMapper;

    @Test
    @DisplayName("验证 session_boundary_pair 表扩展 has_attachment 字段并不破坏历史已核对记录")
    public void testSchemaExtensionAndDataIntegrity() {
        log.info("Starting DDL column addition test...");

        // 1. 尝试增量添加 has_attachment, attachment_meta_a, attachment_meta_b 字段（幂等不破坏原数据）
        try {
            jdbcTemplate.execute("ALTER TABLE `session_boundary_pair` ADD COLUMN `has_attachment` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否包含图片或文件附件'");
            log.info("Successfully added `has_attachment` column.");
        } catch (Exception e) {
            log.info("Column `has_attachment` already exists or skipped: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE `session_boundary_pair` ADD COLUMN `attachment_meta_a` TEXT DEFAULT NULL COMMENT '消息 A 附件列表'");
            log.info("Successfully added `attachment_meta_a` column.");
        } catch (Exception e) {
            log.info("Column `attachment_meta_a` already exists or skipped: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE `session_boundary_pair` ADD COLUMN `attachment_meta_b` TEXT DEFAULT NULL COMMENT '消息 B 附件列表'");
            log.info("Successfully added `attachment_meta_b` column.");
        } catch (Exception e) {
            log.info("Column `attachment_meta_b` already exists or skipped: {}", e.getMessage());
        }

        // 2. 校验已核对的历史记录完整性
        final List<SessionBoundaryPairDO> history = sessionBoundaryPairMapper.selectAuditedHistory(10);
        log.info("Audited history count: {}", history.size());

        for (SessionBoundaryPairDO pair : history) {
            log.info("Audited Pair [ID: {}]: status={}, verdict={}, finalDecision={}",
                    pair.getId(), pair.getL1AuditStatus(), pair.getL1AuditVerdict(), pair.getFinalDecision());
            assertNotNull(pair.getL1AuditStatus(), "Audit status must not be corrupted!");
        }
    }
}
