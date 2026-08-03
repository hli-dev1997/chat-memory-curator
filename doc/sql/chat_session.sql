-- ====================================================================================
-- 数据库初始化 DDL 脚本
-- 目标数据库: chat_memory_curator
-- 结合《阿里巴巴 Java 开发手册（黄山版）》规约设计
-- 重命名字段: user_no 重命名为 user_id (用户 ID / 工号)
-- session_json 字段采用 MySQL 原生 JSON 类型
-- ====================================================================================

CREATE TABLE IF NOT EXISTS `chat_session` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键 ID',
  `user_id`                VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户 ID / 工号 (如 EMP001)',
  `source`                 VARCHAR(32)  NOT NULL DEFAULT 'CHATGPT' COMMENT '数据源头 AI 模型/厂商 (CHATGPT, GEMINI, CLAUDE, KIMI 等)',
  `platform`               VARCHAR(32)  NOT NULL DEFAULT 'WEB' COMMENT '终端/客户端平台 (WEB-网页端, IDE-编辑器插件, APP-移动端 等)',
  `session_id`             VARCHAR(128) NOT NULL COMMENT '会话片段唯一标识 (全平台通用 UUID/ID)',
  `parent_conversation_id` VARCHAR(128) NOT NULL COMMENT '所属原始对话 ID (关联父框框)',
  `title`                  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '对话标题 (方便列表检索与模糊匹配)',
  `message_count`          INT          NOT NULL DEFAULT 0 COMMENT '本片段包含的消息总条数',
  `start_time`             DATETIME     NOT NULL COMMENT '本片段起始消息发送时间',
  `end_time`               DATETIME     NOT NULL COMMENT '本片段结束消息发送时间',
  `session_json`           JSON         NOT NULL COMMENT '全量 JSON 文本 (存储本片段所有的 ChatMessage 列表，原生 JSON 类型支持 SQL 提取)',
  `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间 (阿里规约必备)',
  `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间 (阿里规约必备)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_source` (`source`),
  KEY `idx_platform` (`platform`),
  KEY `idx_parent_conv_id` (`parent_conversation_id`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多平台通用逻辑会话片段表';
