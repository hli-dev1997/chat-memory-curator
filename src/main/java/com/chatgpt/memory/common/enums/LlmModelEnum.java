package com.chatgpt.memory.common.enums;

import lombok.Getter;

/**
 * AI 大语言模型与全模态模型配置枚举
 *
 * @author Antigravity
 */
@Getter
public enum LlmModelEnum {

    /**
     * 最佳平替推荐：通义千问 3.7 Flash 纯文本极速模型（最新一代 Flash）
     */
    QWEN_37_FLASH("qwen3.7-flash", ModelTypeEnum.TEXT, "通义千问 3.7 Flash 纯文本极速模型（最佳平替推荐）", false),

    /**
     * 备选同版本快照：通义千问 3.6 Flash 日期快照版
     */
    QWEN_36_FLASH_SNAPSHOT("qwen3.6-flash-2026-04-16", ModelTypeEnum.TEXT, "通义千问 3.6 Flash 日期快照版", false),

    /**
     * 高准度增强平替：通义千问 3.6 Plus 高精度文本模型
     */
    QWEN_36_PLUS("qwen3.6-plus", ModelTypeEnum.TEXT, "通义千问 3.6 Plus 高精度文本模型", false),

    /**
     * 高准度增强平替：通义千问 3.7 Plus 高精度文本模型
     */
    QWEN_37_PLUS("qwen3.7-plus", ModelTypeEnum.TEXT, "通义千问 3.7 Plus 高精度文本模型", false),

    /**
     * 纯文本极速模型（免费额度已耗尽）
     */
    QWEN_TEXT_FLASH("qwen3.6-flash", ModelTypeEnum.TEXT, "通义千问 3.6 Flash 纯文本极速模型（额度已耗尽）", true),

    /**
     * 全模态首选模型：识图、文字提取、图文上下文推理（默认视觉模型）
     */
    QWEN_OMNI_FLASH("qwen3.5-omni-flash", ModelTypeEnum.MULTIMODAL, "通义千问 3.5 Omni 轻量全模态模型", false),

    /**
     * 高精度全模态模型：复杂表格、长图、手写体、深度图文推理场景
     */
    QWEN_OMNI_PLUS("qwen3.5-omni-plus", ModelTypeEnum.MULTIMODAL, "通义千问 3.5 Omni 高精度全模态模型", false);

    private final String modelName;
    private final ModelTypeEnum modelType;
    private final String description;
    private final boolean quotaExhausted;

    LlmModelEnum(final String modelName,
                 final ModelTypeEnum modelType,
                 final String description,
                 final boolean quotaExhausted) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.description = description;
        this.quotaExhausted = quotaExhausted;
    }
}
