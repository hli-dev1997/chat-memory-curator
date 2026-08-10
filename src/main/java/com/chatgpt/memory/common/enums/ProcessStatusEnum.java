package com.chatgpt.memory.common.enums;

import lombok.Getter;

/**
 * 边界 Pair 处理状态枚举
 * <p>
 * 用于 session_boundary_pair 表的 process_status 字段，
 * 天然支持流水线断点续跑：
 * <ul>
 *   <li>PENDING：L1 已计算，等待 L2 千问精排（仅 FUZZY 区使用）</li>
 *   <li>DONE：L2 裁决完毕，final_decision 已确定（或 L1 直接判定的绿/红区）</li>
 *   <li>NEED_MANUAL_REVIEW：L2 置信度 LOW 或解析失败，需人工抽查介入</li>
 * </ul>
 * 断点续跑时，只需扫描 PENDING 状态的记录重新处理，不影响已完成的 DONE 记录。
 * </p>
 *
 * @author Antigravity
 */
@Getter
public enum ProcessStatusEnum {

    /**
     * 待 L2 处理（仅模糊延伸区 FUZZY 会进入此状态）
     */
    PENDING("PENDING", "待 L2 千问精排处理"),

    /**
     * 已完成（final_decision 已确定）
     */
    DONE("DONE", "处理完成，final_decision 已确定"),

    /**
     * 需人工复核（L2 置信度 LOW 或解析失败兜底）
     */
    NEED_MANUAL_REVIEW("NEED_MANUAL_REVIEW", "需人工复核（置信度低或 L2 解析失败）");

    /**
     * 枚举编码，与数据库 process_status 字段值保持一致
     */
    private final String code;

    /**
     * 中文描述说明
     */
    private final String description;

    ProcessStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
