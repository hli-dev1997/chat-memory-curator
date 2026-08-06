package com.chatgpt.memory.common.enums;

import lombok.Getter;

/**
 * AI 大模型 Prompt 提示词模板枚举
 * <p>
 * 集中管理系统所有的 System Prompt 与 User Prompt 模板，避免在业务代码中硬编码文本。
 * 遵循阿里 Java 开发手册规约：枚举属性私有且不可变 (private final)。
 * </p>
 *
 * <hr>
 * <h3>【如何写好一个高质量 Prompt — 参考 OpenClaw 项目Prompt架构的通用设计逻辑】</h3>
 * <ul>
 *   <li><b>1. 明确角色定位与目标 (Role & Goal)</b>：首先为模型设定精细的角色定位，并清晰定义输入与预期输出的边界。</li>
 *   <li><b>2. 正反例形状判定 (Good Shape vs Bad Shape)</b>：避免泛泛而谈，明确列出合格的判定形状与常见的误判模式
 *       （例如：“不能仅凭关键词或通用语气词相同就误判为同一话题”）。</li>
 *   <li><b>3. 证据链清单 (Evidence Checklist)</b>：要求模型建立多重判定依据（如代词指代、报错延伸、核心意图承接），而非单一表面特征。</li>
 *   <li><b>4. 结构化收敛输出 (Strict JSON / Enum Output)</b>：强制要求模型输出严格的 JSON 字符串（包含枚举状态、置信度与短说明），
 *       消除天然语言的歧义性，确保程序解析安全防爆。</li>
 *   <li><b>5. 边界兜底与优先裁决规则 (Boundary Fallback & Priority Rule)</b>：在证据不足或处于模糊边界时，显式给予模型倾斜裁决法则
 *       （如：“关联证据不明确时优先 MERGE，因为误拆分代价远大于误合并”），将业务设计哲学固化为模型的推理规则。</li>
 * </ul>
 * <hr>
 *
 * @author Antigravity
 */
@Getter
public enum PromptTemplateEnum {

    /**
     * L2 划归模糊延伸区 (0.60 <= Score < 0.82) 的会话关联性判定提示词
     */
    L2_FUZZY_SESSION_SPLIT(
            "L2_FUZZY_SESSION_SPLIT",
            """
            你是一个专业的 AI 对话上下文关联性分析专家。
            你的任务是分析两段相邻的对话片段（Chunk A 与 Chunk B），判断它们是否属于同一个对话主题/意图上下文。

            【判定规则】：
            1. 应当合并 (MERGE - related: true)：
               - Chunk B 是对 Chunk A 的补充、追问、报错反馈或解答延伸。
               - Chunk B 中包含了对 Chunk A 中名词、代码、上下文的指代（例如使用“这个”、“那为什么”、“上面代码”等）。
               - Chunk B 与 Chunk A 在解决同一个技术问题或讨论同一个核心主题。

            2. 应当切断 (SPLIT - related: false)：
               - Chunk B 提出了与 Chunk A 完全无关的新问题或新话题（例如从“Java报错排查”跳跃到“推荐一道菜”或“另一个无关项目的配置”）。
               - 即使使用了相似的语气词或通用提问词，但讨论的实体/核心技术主旨发生了根本转移。

            3. 模糊与边界决策原则（最高优先级）：
               - 如果两个片段之间的关联性证据不够明确、无法确定（处于似关联非关联状态），请【优先判定为 MERGE (合并保留)】。
               - 原因：在会话切分中，错误拆分上下文的代价远大于错误合并。

            【输出要求】：
            必须且只能输出严格的 JSON 字符串，格式如下：
            {
              "related": true / false,
              "action": "MERGE" / "SPLIT",
              "confidence": "HIGH" / "MEDIUM" / "LOW",
              "reason": "用一句话说明判定依据"
            }
            请勿包含 Markdown 代码块标记（如 ```json），直接返回 JSON 内容。
            """,
            """
            向量相似度得分：%s (划归模糊延伸区 0.60 <= Score < 0.82)

            【对话片段 A (前文结尾约 200 字)】：
            %s

            【对话片段 B (当前上下文开头约 200 字)】：
            %s

            请判断 Chunk B 是否与 Chunk A 属于同一会话上下文，并按指定 JSON 格式输出判定结果。
            """,
            "L2 模糊延伸区(0.60~0.82)的千问会话关联性判定提示词模板",
            "v1.0"
    );

    /**
     * 业务场景编码
     */
    private final String code;

    /**
     * 系统提示词 (System Prompt)
     */
    private final String systemPrompt;

    /**
     * 用户提示词模板 (User Prompt Template)
     */
    private final String userPromptTemplate;

    /**
     * 业务描述与备注说明
     */
    private final String description;

    /**
     * 提示词版本号
     */
    private final String version;

    /**
     * 枚举构造方法
     *
     * @param code               业务场景编码
     * @param systemPrompt       系统提示词
     * @param userPromptTemplate 用户提示词模板
     * @param description        业务描述与备注说明
     * @param version            提示词版本号
     */
    PromptTemplateEnum(String code,
                       String systemPrompt,
                       String userPromptTemplate,
                       String description,
                       String version) {
        this.code = code;
        this.systemPrompt = systemPrompt;
        this.userPromptTemplate = userPromptTemplate;
        this.description = description;
        this.version = version;
    }
}
