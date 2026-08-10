# `chat_session` 数据库表全量字段与 Web 界面映射说明文档

> **关联 DDL 脚本**: [`chat_session.sql`](file:///e:/data/chatgpt-memory-exporter/doc/sql/chat_session.sql)  
> **Java Entity 实体**: [`ChatSessionDO.java`](file:///e:/data/chatgpt-memory-exporter/src/main/java/com/chatgpt/memory/model/entity/ChatSessionDO.java)  
> **数据表定位**: **切分产物落地表**（存储语义切分后独立的主题 Session 片段实体及其全量消息 JSON）

---

## 一、 核心澄清：一行记录与网页侧边栏对话的对应关系

> **问：`chat_session` 表里的其中一行记录，是否直接 1:1 对应网页左侧列表里的一个对话？**

* **场景 A：未发生切分（单主题对话）**
  * **是 1:1 对应的**。若网页左侧侧边栏的 1 个完整对话自始至终都在聊同一个话题（如全程讨论“Spring Boot 部署”），在 `chat_session` 表中仅对应 **1 行记录**（`session_id` 格式为 `{parent_conversation_id}-s1`）。
* **场景 B：发生切分（跨主题长对话）**
  * **网页侧边栏的 1 个原始大对话，在 `chat_session` 表里会对应“多行记录”**。
  * 例如在网页左侧同一个对话窗口（`parent_conversation_id` 为 `conv-101`）中，前 10 条聊 Redis，后 10 条聊 Docker。被语义切分算法识别切断后，在 `chat_session` 表里会生成 **2 行记录**：
    * **行 1** (`session_id`: `conv-101-s1`, 标题: "Redis 配置讨论", 包含前 10 条消息)
    * **行 2** (`session_id`: `conv-101-s2`, 标题: "Docker 部署教程", 包含后 10 条消息)
  * 这 2 行记录通过相同的 `parent_conversation_id` 关联回同一个网页侧边栏原始大对话。

---

## 二、 `chat_session` 全量字段含义与 Web 界面映射

| 数据库字段名 | 数据类型 | 约束条件 | 字段说明 | 对应网页/业务 UI 位置 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `NOT NULL AUTO_INCREMENT` | 自增主键 ID | 数据库物理记录主键 |
| `user_id` | `VARCHAR(64)` | `NOT NULL DEFAULT ''` | 用户 ID / 工号（如 `EMP001`） | 系统当前登录用户 / 导出数据归属人 |
| `source` | `VARCHAR(32)` | `NOT NULL DEFAULT 'CHATGPT'` | 数据源头 AI 模型/厂商 | **数据来源标记**（如 `CHATGPT`, `GEMINI`, `CLAUDE`, `KIMI`） |
| `platform` | `VARCHAR(32)` | `NOT NULL DEFAULT 'WEB'` | 终端/客户端类型 | **访问客户端类型**（如 `WEB` 网页端, `IDE` 插件, `APP` 移动端） |
| `session_id` | `VARCHAR(128)` | `NOT NULL UNIQUE` | **会话片段唯一标识** | 逻辑片段唯一 ID（格式：`parent_conversation_id-s1`） |
| `parent_conversation_id` | `VARCHAR(128)` | `NOT NULL` | **所属原始大对话 ID** | **网页左侧侧边栏选中的原始大对话全局唯一 ID** |
| `title` | `VARCHAR(255)` | `NOT NULL DEFAULT ''` | 对话主题标题 | **网页左侧侧边栏显示的对话标题** |
| `message_count` | `INT` | `NOT NULL DEFAULT 0` | 本 Session 片段包含的消息总数 | 当前片段内消息数量（单条 User 问 + Assistant 答算 2 条） |
| `start_time` | `DATETIME` | `NOT NULL` | 本片段起始消息发送时间 | 当前片段第一条消息的真实发送时间 |
| `end_time` | `DATETIME` | `NOT NULL` | 本片段结束消息发送时间 | 当前片段最后一条消息的真实完成时间 |
| `session_json` | `JSON` | `NOT NULL` | **全量消息 JSON 数组文本** | **当前片段主窗口渲染的完整对话消息列表（包含文本、角色及图片指针）** |
| `create_time` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 记录创建时间 | 阿里规约必备：当前数据库记录落库时间 |
| `update_time` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE` | 记录更新时间 | 阿里规约必备：当前数据库记录修补/更新时间 |

---

## 三、 `session_json` 存储结构与 JSON 示例

`session_json` 在 MySQL 5.7+ 中使用原生 `JSON` 数据类型，存储该 Session 片段内部 `List<ChatMessage>` 的完整有序消息列表：

```json
[
  {
    "messageId": "b5532e3d-31e5-4217-bf08-4e26d86e3e6d",
    "role": "user",
    "content": "请看这张 Redis 集群报错截图：![图片](/chat-assets/file-2gRqDToRm9Yv...png)",
    "createTime": "2025-12-14 00:40:57"
  },
  {
    "messageId": "a8231c9f-21d4-8392-ae01-3f412ab710de",
    "role": "assistant",
    "content": "这是一张 Redis Cluster 节点掉线报错，解决方法如下...",
    "createTime": "2025-12-14 00:41:05"
  }
]
```

---

## 四、 常见查询 SQL 范例

### 1. 查询指定原始对话拆分出的所有主题 Session 片段
```sql
SELECT 
    session_id,
    title,
    message_count,
    start_time,
    end_time
FROM chat_session 
WHERE parent_conversation_id = 'conv-multimodal-001'
ORDER BY start_time ASC;
```

### 2. 利用 MySQL 原生 JSON 函数按关键词搜寻 JSON 正文消息
```sql
SELECT 
    session_id,
    title,
    JSON_EXTRACT(session_json, '$[0].content') AS first_message_content
FROM chat_session
WHERE JSON_SEARCH(session_json, 'one', '%Redis%') IS NOT NULL;
```

---

## 五、 数据量推导与核查笔记（全量 1,141 场对话核对实测）

### 1. 两表全量记录数与倍数关系
通过在本地 MySQL `chat_memory_curator` 数据库中实测检索：
* **`chat_session` 消息片段表**: **1,694 条**（涵盖 1,141 场原始大对话，说明全量进行了 553 次主题切分）。
* **`session_boundary_pair` 评估点表**: **19,368 条**（涵盖 1,132 场原始大对话，其中含图片/文件 `has_attachment = 1` 节点为 1,019 条）。
* **数据倍数**: `session_boundary_pair` 表的记录总数是 `chat_session` 的 **11.4 倍**。

### 2. 消息总数与 Pair 缝隙节点数的数学证明与 1,012 差值分解
对于包含 $M$ 条消息的单场对话，其相邻缝隙 Pair 数为 $M - 1$ 条（即经典“植树原理”：消息数永远比配对缝隙数多 1）。全量数据库中：
* `chat_session` 累加 `SUM(message_count)`（消息总条数）= **20,380 条**
* `session_boundary_pair` 全量 `COUNT(1)`（缝隙节点总行数）= **19,368 条**
* **精确差值**: $20,380 - 19,368 = \mathbf{1,012}$

**1,012 精确差值的物理组成来源分析表**:

| 物理组成来源 | 包含场数/节点数 | 贡献差值 | 规则物理说明 |
| :--- | :--- | :--- | :--- |
| **标准多条消息对话** | 1,129 场 | **+1,129** | 标准单线对话消息数 $M$ 比缝隙 Pair 数 $M-1$ 多 1 ($1129 \times 1$) |
| **单条消息对话** | 12 场 | **+12** | 全长只有 1 条消息，无法两两配对，Pair 节点数为 0 ($12 \times 1 - 0$) |
| **ChatGPT 重新生成/多分支** | 多节点 | **-129** | 用户曾点击“重新生成回答”产生多分支上下文，`VectorSessionSplitter` 把分支全量算分留痕，多出 129 个缝隙 Pair 节点 |
| **合计精确推导** | **1,141 场** | **1,012** | **$1129 + 12 - 129 = 1,012$，与数据库实测 $20,380 - 19,368 = 1,012$ 100% 吻合** |

