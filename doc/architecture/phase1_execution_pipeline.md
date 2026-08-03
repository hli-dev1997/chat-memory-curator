# Phase 1 数据提纯与逻辑切分架构设计与执行链路说明

## 一、 Phase 1 核心职责与架构定位

本项目（`chatgpt-memory-exporter`）的总体目标是将用户从 ChatGPT 导出的大型备份数据（`chat.html`，约 87.6 MB）清洗提纯并结构化，最终构建个人对话记忆库。

Phase 1 作为系统的**物理粗加工与数据基础设施阶段**，核心职责包括：
1. **解决大文件内存与性能瓶颈**：流式定位 JavaScript 变量标记（`var jsonData = `），避免 Jsoup 等 DOM 树构建带来的高 GC 压力与 OOM 风险。
2. **解构消息拓扑树**：ChatGPT 对话以树图（DAG）形式存储，Phase 1 负责基于 `current_node` 逆向向上追溯父节点链（`node.parent`），剥离出用户最终采纳的单条主线对话，剔除重新生成与编辑放弃的旁支。
3. **清洗角色与提取多模态文本**：清洗 `system` 提示词与 `tool` 工具中间态输出，仅保留 `user` 与 `assistant` 核心对话角色；提纯纯文本与语音转写字典（`audio_transcription` 对象的 `text` 字段）。
4. **四级时间戳降级阶梯**：按 `Msg Time -> Conv Time -> Prev Msg Time -> Instant.now()` 四级顺序兜底处理缺失时间戳，并具备 `[TIMESTAMP_LEVEL4_FALLBACK]` 特异性日志告警可观测性。
5. **逻辑 Session 切分**：按可配置的无交互时间间隔阈值（默认 4 小时），将跨天或长期的 Conversation 逻辑切割为聚焦的 `ChatSession` 片段，同时通过 `parentConversationId` 与 `-s1/-s2` 编号严格维持血缘映射与时间正序。

---

## 二、 系统全流程执行链路图

```mermaid
flowchart TD
    A["文件源: e:\data\...\chat.html (87.6 MB)"] --> B["[ChatExportParser] Fast String Locating"]
    
    subgraph 1. 高效读取与反序列化
        B -- "定位 var jsonData = 字符串" --> C["BufferedReader 流式截取 JSON 片段"]
        C -- "Jackson ObjectMapper (忽略未知属性)" --> D["List<RawConversationDto> (内存原始拓扑树列表)"]
    end

    subgraph 2. 主线分支提取与清洗算法 (extractMainlineMessages)
        D --> E{"读取 rawConv.getCurrentNode()"}
        E -- "空/缺失防御" --> F["log.warn() 优雅跳过该对话"]
        E -- "校验存在" --> G["逆向回溯: current_node ➔ node.parent ➔ 根节点"]
        G -- "防死循环校验" --> H["visitedNodeIds.contains(id)? 断开环路"]
        H --> I["正序翻转: Collections.reverse(pathNodes)"]
        
        I --> J["节点遍历与角色清洗"]
        J -- "过滤 system / tool" --> K["仅保留 user / assistant 角色"]
        K -- "提纯 textContent" --> L["兼容纯 String 与 dict[text] 语音转写"]
        L -- "四级时间戳降级" --> M["Msg -> Conv -> PrevMsg -> Instant.now()"]
    end

    subgraph 3. 领域模型构建 (Domain Objects)
        M --> N["构建 List<ChatMessage>"]
        N --> O["组装 ChatConversation (完整主线对话实体)"]
    end

    subgraph 4. 时间间隔切分服务 (ChatSessionSplitter)
        O --> P["splitConversation()"]
        P -- "比较相邻消息间隔 gap.toHours()" --> Q{"Math.abs(gap.toHours()) >= 4h ?"}
        Q -- "是: 触发切分" --> R["归档当前 Session，开辟下一个 -s2, -s3 片段"]
        Q -- "否: 归入同一片段" --> S["追加至当前 Session 消息列表"]
        R --> T["生成 List<ChatSession> (带 parentConversationId 映射)"]
        S --> T
    end

    subgraph 5. 序列化落地磁盘 (JSON Output)
        O -- "Jackson (INDENT_OUTPUT)" --> U["all_extracted_conversations.json (69.78 MB, 1,142 场)"]
        T -- "Jackson (INDENT_OUTPUT)" --> V["all_split_sessions.json (36.48 MB, 1,694 个)"]
    end
```

---

## 三、 核心代码模块与包结构

项目源代码遵循《阿里巴巴 Java 开发手册（黄山版）》标准分层与命名规约：

```text
E:\data\chatgpt-memory-exporter
├── doc
│   └── architecture
│       └── phase1_execution_pipeline.md       # 本文档
└── src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── chatgpt
    │   │           └── memory
    │   │               ├── MemoryExporterApplication.java  # Spring Boot 启动入口
    │   │               ├── model                           # 领域模型 (POJO)
    │   │               │   ├── ChatConversation.java       # 主线对话实体 (含 getFormattedFullText)
    │   │               │   ├── ChatMessage.java            # 标准化单条消息实体
    │   │               │   ├── ChatSession.java            # 4小时切分 Session 片段实体
    │   │               │   └── dto                         # 原始 JSON 反序列化 DTO
    │   │               │       ├── RawConversationDto.java
    │   │               │       ├── RawMessageDto.java
    │   │               │       └── RawNodeDto.java
    │   │               ├── parser                          # 核心数据解析器
    │   │               │   └── ChatExportParser.java       # 流式定位、树追溯与清洗算法
    │   │               └── service                         # 逻辑业务服务
    │   │                   └── ChatSessionSplitter.java    # 时间间隔切分服务
    │   └── resources
    │       └── application.yml                             # 端口与切分参数配置 (port: 520)
    └── test
        └── java
            └── com
                └── chatgpt
                    └── memory
                        ├── integration
                        │   ├── ChatExportFullProcessRunnerTest.java  # 全量提纯与导出运行测试
                        │   ├── ChatExportIntegrationTest.java         # 集成测试
                        │   ├── ChatExportVisualPrinterTest.java       # 可视化格式打印测试
                        │   └── ChatExportFirst100PrinterTest.java      # 前 100 场对话导出测试
                        ├── parser
                        │   └── ChatExportParserTest.java              # 解析器单元测试与回归用例
                        └── service
                            └── ChatSessionSplitterTest.java           # 切分服务单元测试
```

---

## 四、 真实提取样例落地分析

数据来源于全量运行后导出的 `all_extracted_conversations.json` 与 `all_split_sessions.json`。

### 真实样例 1：多模态图片清洗与短问答对话 —— 《翻译内容总结》

#### 1. 原始 HTML/JSON 中的数据形态（输入层）
- 包含 7 个 Mapping 节点，包含 `system` 注入词、图片指针 `sediment://file_00000000dae0...`，以及 2 轮问答。
- `current_node` 指向叶子节点 `0eb9cb28-ad40-4a5c-b348-f23458f26d66`。

#### 2. 代码处理转换过程
1. **逆向回溯**: `0eb9cb28` (AI答2) $\to$ `02f11e94` (用户问2) $\to$ `8ab16b02` (AI答1) $\to$ `20e56564` $\to$ `93159fb3` $\to$ `b5532e3d` (用户问1) $\to$ `a17be094` (system) $\to$ `f1a154e1` (system) $\to$ `a7f58465` (system) $\to$ `client-created-root`。
2. **正序翻转与清洗**: 过滤掉 3 个 `system` 节点与 2 个过渡节点，保留 4 个核心对话节点。图片指针被自动过滤，保留自然语言提问 `"翻译一下"`。
3. **领域对象生成**: 封装为 `ChatConversation`（对象包含 4 条 `ChatMessage`）。

#### 3. 最终落地的 JSON 数据结构（输出层）
```json
{
  "conversationId": "693d9713-bda8-8322-be78-98dad7965585",
  "title": "翻译内容总结",
  "createTime": 1765644057.454,
  "messages": [
    {
      "messageId": "b5532e3d-31e5-4217-bf08-4e26d86e3e6d",
      "role": "user",
      "content": "翻译一下",
      "createTime": 1765644057.564
    },
    {
      "messageId": "8ab16b02-5fa1-404e-9acd-5832123d70f5",
      "role": "assistant",
      "content": "给你直接翻译成中文（忠于原意，不加戏）👇\n\n---\n\n### **Gemini 能帮你做什么？**\n\n#### **✨ Gemini 3 已发布**\n查看我们的文档，了解如何在 **Code Assist（代码助手）** 中开始使用 Gemini 3。\n\n**查看文档**\n...",
      "createTime": 1765644057.225
    },
    {
      "messageId": "02f11e94-eda1-4ef1-a5fd-3897350d0bad",
      "role": "user",
      "content": "能选中文吗？",
      "createTime": 1765644121.254
    },
    {
      "messageId": "0eb9cb28-ad40-4a5c-b348-f23458f26d66",
      "role": "assistant",
      "content": "**结论先说：不能。**\n至少目前这个 Gemini Code Assist 的界面本身，没有“语言切换为中文”的选项。\n\n说清楚一点，分三层：\n...",
      "createTime": 1765644120.779
    }
  ]
}
```

---

### 真实样例 2：长文本 Linux 技术排查对话 —— 《虚拟机健康检查》

#### 1. 原始 HTML/JSON 中的数据形态（输入层）
- 包含长达几千字的 Linux `top` 命令输出日志与 JVM 参数，包含 26 条对话节点，多轮交互。

#### 2. 代码处理转换过程
1. **回溯与提纯**: 成功过滤所有底层系统指令，精准保留用户提交的 `top` 文本输出及系统诊断日志。
2. **格式提纯**: 换行符 `\r\n` 与多行缩进被完整保留在 `ChatMessage` 的 `content` 字段中，确保技术代码与日志不丢格式。
3. **Session 切分转化**: 经过 `ChatSessionSplitter` 处理，生成对应的 `ChatSession`，带有指向 `693d2b44-ec88-8324-b330-427a95fc293a` 的映射字段 `parentConversationId`。

#### 3. 最终落地的 JSON 数据结构（输出层）
```json
{
  "conversationId": "693d2b44-ec88-8324-b330-427a95fc293a",
  "title": "虚拟机健康检查",
  "createTime": 1765616454.268,
  "messages": [
    {
      "messageId": "2fade2f2-f2f9-4175-9b29-f33dd1531c09",
      "role": "user",
      "content": "Last login: Fri Dec 12 21:14:12 2025 from 192.168.254.1\r\n[root@node1 ~]# top\r\ntop - 17:00:07 up 6 days, 12:04,  3 users,  load average: 0.01, 0.05, 0.05\r\nTasks: 148 total,   1 running, 147 sleeping,   0 stopped,   0 zombie\r\n%Cpu(s):  0.6 us,  0.3 sy,  0.0 ni, 98.9 id,  0.1 wa,  0.0 hi,  0.1 si,  0.0 st\r\nKiB Mem :  7989844 total,  5362188 free,  1816572 used,   811084 buff/cache\r\nKiB Swap:  2621436 total,  2621436 free,        0 used.  5781184 avail Mem \r\n\r\n   PID USER      PR  NI    VIRT    RES    SHR S  %CPU %MEM     TIME+ COMMAND\r\n  2718 root      20   0 6180004   1.3g  17264 S   5.3 17.0 568:08.71 /hli/software/jdk17/bin/java -Xmx1G -Xms1G...\r\n---我这台虚拟机健康状况如何",
      "createTime": 1765616453.290
    },
    {
      "messageId": "19dfebab-ed28-4ae2-8e8e-fe1a8c07e020",
      "role": "assistant",
      "content": "小李，我直接给你一个**工程师视角的体检结论**，不绕弯子。\n\n---\n\n## 一句话结论（先给结论）\n**这台虚拟机非常健康，资源明显富余，属于“闲着等活干”的状态。**\n...",
      "createTime": 1765616454.135
    }
  ]
}
```

---

## 五、 磁盘全量数据产出规格与运行命令

运行 [ChatExportFullProcessRunnerTest.java](file:///E:/data/chatgpt-memory-exporter/src/test/java/com/chatgpt/memory/integration/ChatExportFullProcessRunnerTest.java) 后，根目录下生成如下两个文件：

| 导出文件绝对路径 | 包含数据量 | 文件大小 | 作用说明 |
| :--- | :--- | :--- | :--- |
| `E:\data\chatgpt-memory-exporter\all_extracted_conversations.json` | **1,142 场** 完整对话 | **69.78 MB** | 提纯后的全局主线对话全文集合。 |
| `E:\data\chatgpt-memory-exporter\all_split_sessions.json` | **1,694 个** 逻辑片段 | **36.48 MB** | 4小时切分后的逻辑 Session 集合，带 `parentConversationId` 映射，作为 Phase 2 大模型 API 输入源。 |

### 全量重新运行指令：
```bash
mvn test -Dtest=ChatExportFullProcessRunnerTest
```
