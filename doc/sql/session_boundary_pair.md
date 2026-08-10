# `session_boundary_pair` 数据库表全量字段与流水线诊断映射说明文档

> **关联 DDL 脚本**: [`session_boundary_pair.sql`](file:///e:/data/chatgpt-memory-exporter/doc/sql/session_boundary_pair.sql)  
> **Java Entity 实体**: [`SessionBoundaryPairDO.java`](file:///e:/data/chatgpt-memory-exporter/src/main/java/com/chatgpt/memory/model/entity/SessionBoundaryPairDO.java)  
> **数据表定位**: **消息边界决策过程表**（记录相邻消息对的 L1 向量相似度、L2 大模型精排裁决、多模态附件留痕与多阶段人工复核全流程）

---

## 一、 核心澄清：一行记录与网页侧边栏对话的对应关系

> **问：`session_boundary_pair` 表里的其中一行记录，是否对应网页左侧列表里的一个对话？**

* **答案是：绝对不是！**
* `session_boundary_pair` 表的一行记录，代表的是聊天主窗口中**相邻两条消息（Message A 与 Message B）之间的“缝隙/切分评估节点”**。
* 若网页左侧点开的某个对话主窗口内包含 **10 条消息**，那么在 `session_boundary_pair` 表里就会产生 **9 行记录**（专门评估这 9 个消息缝隙到底是应该合并 `MERGE` 还是切断 `SPLIT`）。

---

## 二、 `session_boundary_pair` 全量字段含义与流水线映射

| 数据库字段名 | 数据类型 | 约束条件 | 字段说明 | 对应评估流水线 / 诊断界面位置 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `NOT NULL AUTO_INCREMENT` | 自增主键 ID | 评估节点唯一标识 |
| `parent_conversation_id` | `VARCHAR(128)` | `NOT NULL` | 所属原始大对话 ID | 关联 `chat_session.parent_conversation_id` |
| `pair_index` | `INT` | `NOT NULL` | 相邻消息对在对话中的顺序序号（1-indexed） | 第几个评估节点（如 Pair #1 即 Msg 1 与 Msg 2 的边界） |
| `message_a_id` | `VARCHAR(128)` | `NOT NULL DEFAULT ''` | 前一条消息（Message A）的 ID 或 Hash | 边界前一条消息 ID |
| `message_b_id` | `VARCHAR(128)` | `NOT NULL DEFAULT ''` | 后一条消息（Message B）的 ID 或 Hash | 边界后一条消息 ID |
| `message_a_role` | `VARCHAR(16)` | `NOT NULL DEFAULT ''` | 前一条消息的角色（`user` / `assistant`） | Message A 发起角色 |
| `message_b_role` | `VARCHAR(16)` | `NOT NULL DEFAULT ''` | 后一条消息的角色（`user` / `assistant`） | Message B 发起角色 |
| `message_a_text` | `TEXT` | `NOT NULL` | 前一条消息文本摘要/正文 | 前文本内容（截取前 4000 字支持多模态渲染） |
| `message_b_text` | `TEXT` | `NOT NULL` | 后一条消息文本摘要/正文 | 后文本内容（截取前 4000 字支持多模态渲染） |
| `has_attachment` | `TINYINT UNSIGNED` | `NOT NULL DEFAULT 0` | 是否包含图片或文件附件（1是 / 0否） | **卡片筛选**：该边界节点是否带有图片/文件附件 |
| `attachment_meta_a` | `TEXT` | `DEFAULT NULL` | Message A 附件元数据 JSON | Message A 绑定的本地/远程图片及文件信息 |
| `attachment_meta_b` | `TEXT` | `DEFAULT NULL` | Message B 附件元数据 JSON | Message B 绑定的本地/远程图片及文件信息 |
| `l1_score` | `DECIMAL(6,4)` | `NOT NULL` | **L1 BGE 向量余弦相似度得分** | 向量模型算出的相邻上下文语义相关度（精确四位小数） |
| `l1_zone` | `VARCHAR(16)` | `NOT NULL` | **L1 初判大区** | `GREEN_MERGE`(>=0.82) / `FUZZY`(0.60~0.82) / `RED_SPLIT`(<0.60) |
| `l2_verdict` | `VARCHAR(16)` | `DEFAULT NULL` | L2 通义千问大模型精排裁决 | 仅 `FUZZY` 模糊区生效：`MERGE`（建议合并）/ `SPLIT`（建议切断） |
| `l2_confidence` | `VARCHAR(16)` | `DEFAULT NULL` | L2 大模型置信度 | `HIGH`（高置信） / `MEDIUM`（中等） / `LOW`（低置信度转人工） |
| `l2_reason` | `VARCHAR(512)` | `DEFAULT NULL` | L2 裁决推导逻辑一句话说明 | 可视化复核看板上显示的 AI 判断依据 |
| `l2_model` | `VARCHAR(64)` | `DEFAULT NULL` | L2 调用的 AI 大模型名称 | 记录实际调用的模型（如 `qwen3.6-flash` / `qwen3.5-omni-flash` / `qwen3.5-omni-plus`） |
| `l1_audit_status` | `VARCHAR(16)` | `NOT NULL DEFAULT 'UNCHECKED'` | L1 人工核对状态 | `UNCHECKED` / `PASSED` / `OVERRIDDEN` |
| `l1_audit_verdict` | `VARCHAR(16)` | `DEFAULT NULL` | L1 人工核对结论 | `MERGE` / `SPLIT` |
| `l1_audit_time` | `DATETIME` | `DEFAULT NULL` | L1 人工核对时间 | 人工操作时间戳 |
| `l2_audit_status` | `VARCHAR(16)` | `NOT NULL DEFAULT 'UNCHECKED'` | L2 人工核对状态 | `UNCHECKED` / `PASSED` / `OVERRIDDEN` |
| `l2_audit_verdict` | `VARCHAR(16)` | `DEFAULT NULL` | L2 人工核对结论 | `MERGE` / `SPLIT` |
| `l2_audit_time` | `DATETIME` | `DEFAULT NULL` | L2 人工核对时间 | 人工操作时间戳 |
| `manual_remark` | `VARCHAR(256)` | `DEFAULT NULL` | 人工复核备注说明 | 复核人员在界面上输入的强改或核对理由 |
| `final_decision` | `VARCHAR(16)` | `DEFAULT NULL` | **最终切分决策** | **`MERGE`（同属一个 Session） / `SPLIT`（切断开辟新 Session）** |
| `process_status` | `VARCHAR(32)` | `NOT NULL DEFAULT 'PENDING'` | 边界节点流水线处理状态 | `PENDING`（待 L2 裁决） / `DONE`（完成） / `NEED_MANUAL_REVIEW`（待人工复核） |
| `create_time` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 记录创建时间 | 阿里规约必备：当前 Pair 节点算分落库时间 |
| `update_time` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE` | 记录更新时间 | 阿里规约必备：当前 Pair 节点裁决更新时间 |

---

## 三、 常见查询 SQL 范例

### 1. 查询指定原始对话在切分评估流水线中的所有边界裁决节点
```sql
SELECT 
    pair_index,
    message_a_role,
    message_b_role,
    l1_score,
    l1_zone,
    l2_verdict,
    final_decision,
    process_status
FROM session_boundary_pair
WHERE parent_conversation_id = 'conv-multimodal-001'
ORDER BY pair_index ASC;
```

### 2. 统计包含多模态图片且需要人工复核的临界区 Boundary Pair 队列
```sql
SELECT 
    id,
    parent_conversation_id,
    pair_index,
    message_a_text,
    message_b_text,
    l1_score,
    l2_reason
FROM session_boundary_pair
WHERE has_attachment = 1 
  AND process_status = 'NEED_MANUAL_REVIEW'
ORDER BY l1_score ASC;
```

---

## 四、 数据量推导与核查笔记（全量 1,141 场对话核对实测）

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

