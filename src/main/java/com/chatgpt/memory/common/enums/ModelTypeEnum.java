package com.chatgpt.memory.common.enums;

import lombok.Getter;

/**
 * AI 大模型能力类型枚举
 *
 * @author Antigravity
 */
@Getter
public enum ModelTypeEnum {

    /**
     * 纯文本语言模型
     */
    TEXT("纯语言模型"),

    /**
     * 图文全模态/多模态模型
     */
    MULTIMODAL("全模态模型");

    private final String description;

    ModelTypeEnum(final String description) {
        this.description = description;
    }
}
