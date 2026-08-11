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
     * 最佳平替推荐：通义千问 3.6 27B 参数轻量模型（1,000,000 免费 Token 最佳平替）
     */
    QWEN_36_27B("qwen3.6-27b", ModelTypeEnum.TEXT, "通义千问 3.6 27B 轻量文本模型（最佳平替推荐）", false),

    /**
     * 备选平替：通义千问 3 235B 超大参数开源模型
     */
    QWEN_3_235B_A22B("qwen3-235b-a22b", ModelTypeEnum.TEXT, "通义千问 3 235B 超大参数开源模型", false),

    /**
     * 备选平替：通义千问 Coder Plus 代码增强模型
     */
    QWEN_CODER_PLUS("qwen-coder-plus", ModelTypeEnum.TEXT, "通义千问 Coder Plus 代码模型", false),

    /**
     * 备选平替：通义千问 Plus 日期快照版
     */
    QWEN_PLUS_SNAPSHOT("qwen-plus-2025-01-25", ModelTypeEnum.TEXT, "通义千问 Plus 日期快照版", false),

    /**
     * 旗舰平替：通义千问 3.7 Max 顶级模型快照版
     */
    QWEN_37_MAX_SNAPSHOT("qwen3.7-max-2026-05-17", ModelTypeEnum.TEXT, "通义千问 3.7 Max 旗舰模型快照版", false),

    /**
     * 高准度增强平替：通义千问 3.6 Plus 高精度文本模型
     */
    QWEN_36_PLUS("qwen3.6-plus", ModelTypeEnum.TEXT, "通义千问 3.6 Plus 高精度文本模型", false),

    /**
     * 高准度增强平替：通义千问 3.7 Plus 高精度文本模型
     */
    QWEN_37_PLUS("qwen3.7-plus", ModelTypeEnum.TEXT, "通义千问 3.7 Plus 高精度文本模型", false),

    /**
     * 纯文本极速模型快照版（免费额度已耗尽）
     */
    QWEN_36_FLASH_SNAPSHOT("qwen3.6-flash-2026-04-16", ModelTypeEnum.TEXT, "通义千问 3.6 Flash 日期快照版（额度已耗尽）", true),

    /**
     * 纯文本极速模型（免费额度已耗尽）
     */
    QWEN_37_FLASH("qwen3.7-flash", ModelTypeEnum.TEXT, "通义千问 3.7 Flash 纯文本极速模型（额度已耗尽）", true),

    /**
     * 纯文本极速模型（免费额度已耗尽）
     */
    QWEN_TEXT_FLASH("qwen3.6-flash", ModelTypeEnum.TEXT, "通义千问 3.6 Flash 纯文本极速模型（额度已耗尽）", true),

    /**
     * 最佳平替推荐：通义千问 3 VL Plus 高精度全模态模型（最新一代视觉多模态）
     */
    QWEN_3_VL_PLUS("qwen3-vl-plus", ModelTypeEnum.MULTIMODAL, "通义千问 3 VL Plus 高精度全模态模型（最佳平替推荐）", false),

    /**
     * 全模态轻量模型（免费额度已耗尽）
     */
    QWEN_OMNI_FLASH("qwen3.5-omni-flash", ModelTypeEnum.MULTIMODAL, "通义千问 3.5 Omni 轻量全模态模型（额度已耗尽）", true),

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

    /**
     * 根据模型名称字符串解析模型枚举项，解析失败时返回兜底默认枚举
     *
     * @param modelName 待解析的模型名称或枚举 Key 字符串
     * @param fallback  兜底模型枚举项
     * @return 对应的模型枚举项或 fallback 兜底项
     */
    public static LlmModelEnum fromModelName(final String modelName, final LlmModelEnum fallback) {
        if (modelName == null || modelName.isBlank()) {
            return fallback;
        }
        final String trimmed = modelName.trim();
        for (final LlmModelEnum item : values()) {
            if (item.name().equalsIgnoreCase(trimmed) || item.getModelName().equalsIgnoreCase(trimmed)) {
                return item;
            }
        }
        return fallback;
    }
}
