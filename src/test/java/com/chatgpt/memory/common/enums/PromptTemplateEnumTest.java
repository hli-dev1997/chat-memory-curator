package com.chatgpt.memory.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词模板枚举类单元测试
 *
 * @author Antigravity
 */
public class PromptTemplateEnumTest {

    @Test
    @DisplayName("测试 L2 模糊延伸区提示词枚举配置有效性")
    public void testL2FuzzySessionSplitEnum() {
        PromptTemplateEnum templateEnum = PromptTemplateEnum.L2_FUZZY_SESSION_SPLIT;

        assertNotNull(templateEnum, "枚举实例不应为 null");
        assertEquals("L2_FUZZY_SESSION_SPLIT", templateEnum.getCode());
        assertNotNull(templateEnum.getSystemPrompt(), "System Prompt 不应为 null");
        assertNotNull(templateEnum.getUserPromptTemplate(), "User Prompt Template 不应为 null");

        assertTrue(templateEnum.getSystemPrompt().contains("模糊与边界决策原则"),
                "System Prompt 应包含模糊与边界决策原则说明");
        assertTrue(templateEnum.getSystemPrompt().contains("优先判定为 MERGE"),
                "System Prompt 应显式包含优先判定为 MERGE 的强约束法则");
    }
}
