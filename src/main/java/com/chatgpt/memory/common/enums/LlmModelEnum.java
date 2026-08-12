package com.chatgpt.memory.common.enums;

import lombok.Getter;

/**
 * AI 大语言模型与全模态模型配置枚举
 * <p>
 * 精准收录 DashScope 阿里云大模型服务平台账号下所有模型信息。
 * 严禁包含任何不在用户账单/免费额度列表中的其他模型。
 * </p>

 * @author Antigravity
 */
@Getter
public enum LlmModelEnum {

    // #region 纯文本大语言模型 - 具备免费额度
    QWEN_MATH_TURBO("qwen-math-turbo", ModelTypeEnum.TEXT, "qwen-math-turbo (1,000,000 免费额度)", false),
    QWEN_37_PLUS("qwen3.7-plus", ModelTypeEnum.TEXT, "qwen3.7-plus (1,000,000 免费额度)", false),
    QWEN3_VL_235B_A22B_THINKING("qwen3-vl-235b-a22b-thinking", ModelTypeEnum.TEXT, "qwen3-vl-235b-a22b-thinking (1,000,000 免费额度)", false),
    QWEN3_VL_32B_THINKING("qwen3-vl-32b-thinking", ModelTypeEnum.TEXT, "qwen3-vl-32b-thinking (1,000,000 免费额度)", false),
    QWEN_PLUS_2025_07_28("qwen-plus-2025-07-28", ModelTypeEnum.TEXT, "qwen-plus-2025-07-28 (1,000,000 免费额度)", false),
    DEEPSEEK_R1_DISTILL_QWEN_7B("deepseek-r1-distill-qwen-7b", ModelTypeEnum.TEXT, "deepseek-r1-distill-qwen-7b (免费额度已用尽)", true),
    GLM_5("glm-5", ModelTypeEnum.TEXT, "glm-5 (1,000,000 免费额度)", false),
    QWEN_MAX("qwen-max", ModelTypeEnum.TEXT, "qwen-max (免费额度已用尽)", true),
    QWEN_MT_FLASH("qwen-mt-flash", ModelTypeEnum.TEXT, "qwen-mt-flash (1,000,000 免费额度)", false),
    QWEN3_VL_30B_A3B_THINKING("qwen3-vl-30b-a3b-thinking", ModelTypeEnum.TEXT, "qwen3-vl-30b-a3b-thinking (1,000,000 免费额度)", false),
    QWEN_VL_OCR_LATEST("qwen-vl-ocr-latest", ModelTypeEnum.TEXT, "qwen-vl-ocr-latest (1,000,000 免费额度)", false),
    QWEN3_32B("qwen3-32b", ModelTypeEnum.TEXT, "qwen3-32b (1,000,000 免费额度)", false),
    DEEPSEEK_R1_DISTILL_QWEN_32B("deepseek-r1-distill-qwen-32b", ModelTypeEnum.TEXT, "deepseek-r1-distill-qwen-32b (1,000,000 免费额度)", false),
    QWEN_VL_PLUS("qwen-vl-plus", ModelTypeEnum.TEXT, "qwen-vl-plus (1,000,000 免费额度)", false),
    QWEN_37_FLASH_2026_07_15("qwen3.7-flash-2026-07-15", ModelTypeEnum.TEXT, "qwen3.7-flash-2026-07-15 (1,000,000 免费额度)", false),
    QWEN_LONG("qwen-long", ModelTypeEnum.TEXT, "qwen-long (1,000,000 免费额度)", false),
    QWEN_35_35B_A3B("qwen3.5-35b-a3b", ModelTypeEnum.TEXT, "qwen3.5-35b-a3b (1,000,000 免费额度)", false),
    GLM_45_AIR("glm-4.5-air", ModelTypeEnum.TEXT, "glm-4.5-air (1,000,000 免费额度)", false),
    QWEN3_CODER_480B_A35B_INSTRUCT("qwen3-coder-480b-a35b-instruct", ModelTypeEnum.TEXT, "qwen3-coder-480b-a35b-instruct (1,000,000 免费额度)", false),
    QWEN3_CODER_PLUS("qwen3-coder-plus", ModelTypeEnum.TEXT, "qwen3-coder-plus (1,000,000 免费额度)", false),
    QWEN3_VL_8B_THINKING("qwen3-vl-8b-thinking", ModelTypeEnum.TEXT, "qwen3-vl-8b-thinking (1,000,000 免费额度)", false),
    DEEPSEEK_V4_FLASH_0731("deepseek-v4-flash-0731", ModelTypeEnum.TEXT, "deepseek-v4-flash-0731 (1,000,000 免费额度)", false),
    QWEN_35_FLASH_2026_02_23("qwen3.5-flash-2026-02-23", ModelTypeEnum.TEXT, "qwen3.5-flash-2026-02-23 (1,000,000 免费额度)", false),
    QWEN_VL_OCR_1028("qwen-vl-ocr-1028", ModelTypeEnum.TEXT, "qwen-vl-ocr-1028 (1,000,000 免费额度)", false),
    QWEN3_VL_FLASH_2025_10_15("qwen3-vl-flash-2025-10-15", ModelTypeEnum.TEXT, "qwen3-vl-flash-2025-10-15 (1,000,000 免费额度)", false),
    QWEN3_MAX_PREVIEW("qwen3-max-preview", ModelTypeEnum.TEXT, "qwen3-max-preview (1,000,000 免费额度)", false),
    QWEN3_8B("qwen3-8b", ModelTypeEnum.TEXT, "qwen3-8b (1,000,000 免费额度)", false),
    QWEN_PLUS_0112("qwen-plus-0112", ModelTypeEnum.TEXT, "qwen-plus-0112 (1,000,000 免费额度)", false),
    QWEN_PLUS("qwen-plus", ModelTypeEnum.TEXT, "qwen-plus (免费额度已用尽)", true),
    GUI_PLUS("gui-plus", ModelTypeEnum.TEXT, "gui-plus (1,000,000 免费额度)", false),
    QWEN_MATH_PLUS("qwen-math-plus", ModelTypeEnum.TEXT, "qwen-math-plus (1,000,000 免费额度)", false),
    QWEN_TURBO("qwen-turbo", ModelTypeEnum.TEXT, "qwen-turbo (免费额度已用尽)", true),
    // #endregion

    // #region 全模态大模型 - 具备免费额度
    OMNI_QWEN35_PLUS_2026_03_15("qwen3.5-omni-plus-2026-03-15", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-plus-2026-03-15 (1,000,000 免费额度)", false),
    OMNI_QWEN3_FLASH_REALTIME_2025_09_15("qwen3-omni-flash-realtime-2025-09-15", ModelTypeEnum.MULTIMODAL, "qwen3-omni-flash-realtime-2025-09-15 (1,000,000 免费额度)", false),
    OMNI_QWEN3_FLASH_REALTIME("qwen3-omni-flash-realtime", ModelTypeEnum.MULTIMODAL, "qwen3-omni-flash-realtime (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO_REALTIME_2025_05_08("qwen-omni-turbo-realtime-2025-05-08", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo-realtime-2025-05-08 (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO_REALTIME_LATEST("qwen-omni-turbo-realtime-latest", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo-realtime-latest (1,000,000 免费额度)", false),
    OMNI_QWEN35_FLASH_REALTIME_2026_03_15("qwen3.5-omni-flash-realtime-2026-03-15", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-flash-realtime-2026-03-15 (1,000,000 免费额度)", false),
    OMNI_QWEN3_FLASH_2025_12_01("qwen3-omni-flash-2025-12-01", ModelTypeEnum.MULTIMODAL, "qwen3-omni-flash-2025-12-01 (1,000,000 免费额度)", false),
    OMNI_QWEN3_FLASH("qwen3-omni-flash", ModelTypeEnum.MULTIMODAL, "qwen3-omni-flash (1,000,000 免费额度)", false),
    OMNI_QWEN35_PLUS("qwen3.5-omni-plus", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-plus (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO_REALTIME("qwen-omni-turbo-realtime", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo-realtime (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO_LATEST("qwen-omni-turbo-latest", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo-latest (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO("qwen-omni-turbo", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo (1,000,000 免费额度)", false),
    OMNI_QWEN25_7B("qwen2.5-omni-7b", ModelTypeEnum.MULTIMODAL, "qwen2.5-omni-7b (1,000,000 免费额度)", false),
    OMNI_QWEN35_PLUS_REALTIME("qwen3.5-omni-plus-realtime", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-plus-realtime (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO_2025_03_26("qwen-omni-turbo-2025-03-26", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo-2025-03-26 (1,000,000 免费额度)", false),
    OMNI_QWEN3_FLASH_REALTIME_2025_12_01("qwen3-omni-flash-realtime-2025-12-01", ModelTypeEnum.MULTIMODAL, "qwen3-omni-flash-realtime-2025-12-01 (1,000,000 免费额度)", false),
    OMNI_QWEN_TURBO_2025_01_19("qwen-omni-turbo-2025-01-19", ModelTypeEnum.MULTIMODAL, "qwen-omni-turbo-2025-01-19 (1,000,000 免费额度)", false),
    OMNI_QWEN35_PLUS_REALTIME_2026_03_15("qwen3.5-omni-plus-realtime-2026-03-15", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-plus-realtime-2026-03-15 (1,000,000 免费额度)", false),
    OMNI_QWEN35_FLASH_REALTIME("qwen3.5-omni-flash-realtime", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-flash-realtime (1,000,000 免费额度)", false),
    OMNI_QWEN3_FLASH_2025_09_15("qwen3-omni-flash-2025-09-15", ModelTypeEnum.MULTIMODAL, "qwen3-omni-flash-2025-09-15 (1,000,000 免费额度)", false),
    OMNI_QWEN35_FLASH_2026_03_15("qwen3.5-omni-flash-2026-03-15", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-flash-2026-03-15 (1,000,000 免费额度)", false),
    // #endregion

    // #region 全模态大模型 - 免费额度已耗尽
    OMNI_QWEN35_FLASH("qwen3.5-omni-flash", ModelTypeEnum.MULTIMODAL, "qwen3.5-omni-flash (免费额度已用尽)", true);
    // #endregion

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
     * 根据模型类型与模型名称字符串精准解析模型枚举项，解析失败时返回兜底默认枚举
     *
     * @param modelName 待解析的模型名称或枚举 Key 字符串
     * @param type      模型类型 (TEXT / MULTIMODAL)，可为 null
     * @param fallback  兜底模型枚举项
     * @return 对应的模型枚举项或 fallback 兜底项
     */
    public static LlmModelEnum fromModelName(final String modelName, final ModelTypeEnum type, final LlmModelEnum fallback) {
        if (modelName == null || modelName.isBlank()) {
            return fallback;
        }
        final String trimmed = modelName.trim();
        for (final LlmModelEnum item : values()) {
            if ((type == null || item.getModelType() == type) &&
                (item.name().equalsIgnoreCase(trimmed) || item.getModelName().equalsIgnoreCase(trimmed))) {
                return item;
            }
        }
        return fallback;
    }

    /**
     * 根据模型名称字符串解析模型枚举项，解析失败时返回兜底默认枚举
     *
     * @param modelName 待解析的模型名称或枚举 Key 字符串
     * @param fallback  兜底模型枚举项
     * @return 对应的模型枚举项或 fallback 兜底项
     */
    public static LlmModelEnum fromModelName(final String modelName, final LlmModelEnum fallback) {
        return fromModelName(modelName, null, fallback);
    }
}
