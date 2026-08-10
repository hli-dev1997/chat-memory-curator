-- ====================================================================================
-- 会话切分消息边界决策过程表 DDL 脚本
-- 目标数据库: chat_memory_curator
-- 设计目的:
--   将"决策过程 (Pair 明细)"与"决策产物 (ChatSession)"解耦存储，
--   支持 L1 向量初筛、L2 千问大模型精排、断点续跑与多阶段人工双向复核与数据库全留痕。
-- 结合《阿里巴巴 Java 开发手册（黄山版）》规约设计：
--   - 表名小写单数、字段名下划线风格
--   - id / create_time / update_time 必备三字段
--   - 禁止外键，逻辑关联通过 parent_conversation_id 在应用层维护
--   - 相似度分数采用 decimal(6,4) 精确存储，禁止 float/double
-- ====================================================================================

CREATE TABLE IF NOT EXISTS `session_boundary_pair` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键 ID',
  `parent_conversation_id` VARCHAR(128) NOT NULL COMMENT '所属原始对话 ID（关联 chat_session.parent_conversation_id）',
  `pair_index`             INT          NOT NULL COMMENT '在当前 Conversation 中的 Pair 顺序序号（1-indexed，保证顺序性）',
  `message_a_id`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '前一条消息 ID 或内容哈希',
  `message_b_id`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '后一条消息 ID 或内容哈希',
  `message_a_role`         VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '前一条消息角色（user / assistant）',
  `message_b_role`         VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '后一条消息角色（user / assistant）',
  `message_a_text`         TEXT         NOT NULL COMMENT '前一条消息完整文本正文（包含 Markdown 图片指针与完整内容）',
  `message_b_text`         TEXT         NOT NULL COMMENT '后一条消息完整文本正文（包含 Markdown 图片指针与完整内容）',
  `has_attachment`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否包含图片或文件附件（1是/0否，阿里规范）',
  `attachment_meta_a`      TEXT         DEFAULT NULL COMMENT '消息 A 关联的全量附件元数据列表（JSON 数组）',
  `attachment_meta_b`      TEXT         DEFAULT NULL COMMENT '消息 B 关联的全量附件元数据列表（JSON 数组）',
  `l1_score`               DECIMAL(6,4) NOT NULL COMMENT 'L1 BGE 向量余弦相似度得分（精确四位小数）',
  `l1_zone`                VARCHAR(16)  NOT NULL COMMENT 'L1 大区初判（GREEN_MERGE：>= 0.82 / FUZZY：0.60~0.82 / RED_SPLIT：< 0.60）',
  `l2_verdict`             VARCHAR(16)  DEFAULT NULL COMMENT 'L2 千问大模型裁决（MERGE / SPLIT），仅 FUZZY 区需要，绿区/红区留空',
  `l2_confidence`          VARCHAR(16)  DEFAULT NULL COMMENT 'L2 置信度（HIGH / MEDIUM / LOW）',
  `l2_reason`              VARCHAR(512) DEFAULT NULL COMMENT 'L2 裁决推导说明（一句话，方便人工核查）',
  `l1_audit_status`        VARCHAR(16)  NOT NULL DEFAULT 'UNCHECKED' COMMENT 'L1 人工核对状态（UNCHECKED：未核对 / PASSED：认可初判 / OVERRIDDEN：推翻修改）',
  `l1_audit_verdict`       VARCHAR(16)  DEFAULT NULL COMMENT 'L1 人工判定结论（MERGE / SPLIT）',
  `l1_audit_time`          DATETIME     DEFAULT NULL COMMENT 'L1 人工核对时间戳',
  `l2_audit_status`        VARCHAR(16)  NOT NULL DEFAULT 'UNCHECKED' COMMENT 'L2 人工核对状态（UNCHECKED：未核对 / PASSED：认可大模型 / OVERRIDDEN：推翻修改）',
  `l2_audit_verdict`       VARCHAR(16)  DEFAULT NULL COMMENT 'L2 人工判定结论（MERGE / SPLIT）',
  `l2_audit_time`          DATETIME     DEFAULT NULL COMMENT 'L2 人工核对时间戳',
  `manual_remark`          VARCHAR(256) DEFAULT NULL COMMENT '人工复核备注/说明',
  `final_decision`         VARCHAR(16)  DEFAULT NULL COMMENT '最终决策（MERGE / SPLIT）：结合 L1、L2 及人工覆写后的最终有效决策',
  `process_status`         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态（PENDING：待处理 / DONE：已完成 / NEED_MANUAL_REVIEW：需人工复核 / MANUAL_OVERRIDE：人工已覆写）',
  `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间（阿里规约必备）',
  `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间（阿里规约必备）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_pair` (`parent_conversation_id`, `pair_index`) COMMENT '同一对话内 Pair 序号唯一约束，保证顺序一致性',
  KEY `idx_parent_conv_id` (`parent_conversation_id`) COMMENT '按对话 ID 聚合查询索引',
  KEY `idx_process_status` (`process_status`) COMMENT '状态扫描索引',
  KEY `idx_l1_zone_score` (`l1_zone`, `l1_score`) COMMENT '临界区精准抽查：按区间+得分范围快速过滤',
  KEY `idx_has_attachment` (`has_attachment`) COMMENT '含附件/图片对话卡片极速筛选索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会话切分消息边界决策过程表（L1向量初筛 + L2大模型精排 + L1/L2多阶段人工复核）';
