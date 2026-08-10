package com.chatgpt.memory.common.enums;

import lombok.Getter;

/**
 * L1 BGE 向量初筛大区枚举
 * <p>
 * 定义相邻消息对在 L1 阶段按余弦相似度得分划分的三个区间，
 * 与 application.yml 中 chat.segmentation 配置的门限值保持一致：
 * <ul>
 *   <li>GREEN_MERGE：得分 >= 0.82，强相关，L1 直接判定合并，无需 LLM</li>
 *   <li>FUZZY：0.60 <= 得分 < 0.82，模糊延伸区，需送 L2 千问精排</li>
 *   <li>RED_SPLIT：得分 < 0.60，跨主题，L1 直接判定切断，无需 LLM</li>
 * </ul>
 * </p>
 *
 * @author Antigravity
 */
@Getter
public enum L1ZoneEnum {

    /**
     * 绿区：强相关，L1 直接合并，final_decision = MERGE
     */
    GREEN_MERGE("GREEN_MERGE", "绿区 - 强相关直接合并"),

    /**
     * 黄区：模糊延伸区，final_decision 由 L2 千问裁决决定
     */
    FUZZY("FUZZY", "黄区 - 模糊延伸区，需 L2 千问精排"),

    /**
     * 红区：跨主题切断，L1 直接切断，final_decision = SPLIT
     */
    RED_SPLIT("RED_SPLIT", "红区 - 跨主题强制切断");

    /**
     * 枚举编码，与数据库 l1_zone 字段值保持一致
     */
    private final String code;

    /**
     * 中文描述说明
     */
    private final String description;

    L1ZoneEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
