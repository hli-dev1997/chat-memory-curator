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
     * 纯文本极速模型：适合无图片的日常会话切分与理解
     */
    QWEN_TEXT_FLASH("qwen3.6-flash", ModelTypeEnum.TEXT, "通义千问 3.6 Flash 纯文本极速模型"),

    /**
     * 全模态首选模型：识图、文字提取、图文上下文推理（默认视觉模型）
     */
    QWEN_OMNI_FLASH("qwen3.5-omni-flash", ModelTypeEnum.MULTIMODAL, "通义千问 3.5 Omni 轻量全模态模型"),

    /**
     * 高精度全模态模型：复杂表格、长图、手写体、深度图文推理场景
     */
    QWEN_OMNI_PLUS("qwen3.5-omni-plus", ModelTypeEnum.MULTIMODAL, "通义千问 3.5 Omni 高精度全模态模型");

    private final String modelName;
    private final ModelTypeEnum modelType;
    private final String description;

    LlmModelEnum(final String modelName, final ModelTypeEnum modelType, final String description) {
        this.modelName = modelName;
        this.modelType = modelType;
        this.description = description;
    }
}
