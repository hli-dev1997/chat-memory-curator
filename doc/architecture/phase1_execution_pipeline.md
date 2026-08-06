# Phase 1 数据提纯与 BGE 向量语义切分架构设计与执行链路说明

## 一、 Phase 1 核心职责与架构定位

本项目（`chatgpt-memory-exporter`）的目标是将用户从 ChatGPT 导出的大型备份数据（`chat.html`，约 87.6 MB）清洗提纯并结构化，构建智能对话记忆库。

Phase 1 作为系统的**物理粗加工与双层切分的第一层（L1 Fast Filter）阶段**，核心职责包括：
1. **大文件流式解析与主线解构**：通过 `ChatExportParser` 快速定位 `var jsonData = `，逆向从 `current_node` 追溯父节点链，剥离单条采纳主线对话，剔除旁支与编辑废稿。
2. **清洗多模态与时间戳降级**：过滤 `system` 提示词与 `tool` 工具中间态输出，仅保留 `user` / `assistant` 核心对话；按 `Msg Time -> Conv Time -> Prev Msg Time -> Instant.now()` 四级顺序兜底处理缺失时间戳。
3. **L1 BGE 向量语义切分（双门限 + 防护机制）**：
   - 抛弃脆弱的硬编码时间打断，采用本地 **BGE-Small-ZH 512维 Dense 向量**计算相邻消息余弦相似度（`CosineSimilarity`）。
   - **双门限双锁机制**：`>= 0.82`（绝对强相关放行）、`0.60 ~ 0.82`（划归模糊延伸观察区，留给 Phase 2 L2 LLM 精排）、`< 0.60`（跨主题强制物理切断）。
   - **短文本防虚低**：`< 30` 字口语抽象追问自动向前拼接 Context 摘要重新提取向量。
   - **超长文防溢出与尾部丢失**：`> 350` 字长回复按每 350 字分批切片，计算 512 维均值向量（Mean Pooling），确保尾部总结与代码语义 0 丢失且 ONNX 不溢出。
4. **全量动态 Web 可视化诊断仪**：
   - 提供固定书签网址 **`http://localhost:520/segmentation-visualizer.html`**，重启服务后刷新即可动态核对全量 1,142 场对话的实时切分与 BGE 得分 Badge。

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

    subgraph 4. L1 BGE 向量语义切分服务 (VectorSessionSplitter - 真相源)
        O --> P["inspectConversation()"]
        P -- "1. 短句 <30字" --> Q["拼接 Context 前100字"]
        P -- "2. 长句 >350字" --> R["分批 350 字算均值向量 Mean Pooling"]
        Q & R --> S["CosineSimilarity(base, target)"]
        S --> T{"余弦得分 score 判定"}
        T -- "score >= 0.82" --> U["🟢 强相关 (归入当前 Session)"]
        T -- "0.60 <= score < 0.82" --> V["🟡 模糊延伸区 (归入 Candidate Session, 留待 L2 LLM)"]
        T -- "score < 0.60" --> W["🔴 跨主题切断 (物理打断生成新 Session)"]
        U & V & W --> X["产出 VectorSegmentationResult (Sessions + PairDetails)"]
    end

    subgraph 5. Web 诊断与 MySQL 直存输出
        X -- "SegmentationVisualizerController" --> Y["Web 诊断仪 (http://localhost:520/segmentation-visualizer.html)"]
        X -- "ChatImportFacadeService" --> Z["ChatSessionDatabaseService 批量 Upsert 落库 MySQL"]
    end
```

---

## 三、 核心代码模块与包结构

源代码严格遵循《阿里巴巴 Java 开发手册（黄山版）》规约：

```text
E:\data\chatgpt-memory-exporter
├── doc
│   └── architecture
│       └── phase1_execution_pipeline.md       # 本文档 (全量链路说明)
└── src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── chatgpt
    │   │           └── memory
    │   │               ├── MemoryExporterApplication.java  # Spring Boot 启动入口
    │   │               ├── config                           # 配置层
    │   │               │   ├── EmbeddingConfig.java         # 单例 BGE 512维模型 Bean
    │   │               │   └── OpenApiConfig.java           # Swagger/Knife4j 文档配置
    │   │               ├── controller                       # 控制层
    │   │               │   ├── ChatExportController.java    # 直存 MySQL 与解析 REST
    │   │               │   └── SegmentationVisualizerController.java # 全量 1,142 场对话动态 Web 诊断 API
    │   │               ├── mapper                           # MyBatis-Plus DAO 层
    │   │               │   └── ChatSessionMapper.java
    │   │               ├── model                            # 领域实体
    │   │               │   ├── ChatConversation.java        # 提纯后完整对话实体
    │   │               │   ├── ChatMessage.java             # 单条消息实体
    │   │               │   ├── ChatSession.java             # 切分后逻辑 Session DO
    │   │               │   ├── ConversationPairDetail.java  # 语句 Pair 得分与 Tag 明细 POJO
    │   │               │   └── VectorSegmentationResult.java# 切分结果与明细聚合实体
    │   │               ├── parser                           # 核心解析器
    │   │               │   └── ChatExportParser.java        # Jsoup 流式高并发 HTML DOM 提取服务
    │   │               └── service                          # 业务服务层
    │   │                   ├── VectorSessionSplitter.java   # 【唯一的语义切分真相源】
    │   │                   ├── ChatImportFacadeService.java # 一键落库直存门面服务
    │   │                   └── ChatSessionDatabaseService.java # 数据库批量落库服务
    │   └── resources
    │       ├── application.yml                              # 门限配置 (high: 0.82, low: 0.60, minLength: 30)
    │       └── static
    │           └── segmentation-visualizer.html             # 全量对话 Web 动态诊断单页 UI
    └── test
        └── java
            └── com
                └── chatgpt
                    └── memory
                        ├── integration
                        │   ├── ChatSessionDatabaseImportIntegrationTest.java # 数据库落库集成测试
                        │   └── FullFileVectorSegmentationTest.java          # 全量 chat.html 向量切分集成测试
                        └── parser
                            └── ChatExportParserTest.java                     # DOM 解析器单元测试
```

---

## 四、 诊断与实测验证指令

无需生成中转 JSON，全量诊断可以直接通过内置命令或固定网页进行：

### 1. 网页固定诊断 UI：
固定书签网址：**`http://localhost:520/segmentation-visualizer.html`**

### 2. Maven 全量集成自动化测试指令：
```bash
mvn test -Dtest=FullFileVectorSegmentationTest
```
