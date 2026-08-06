package com.chatgpt.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 类说明 / Class Description:
 * 中文：VectorSessionSplitter 拆分过程中相邻语句 Pair 的 BGE 余弦得分与判定明细 POJO。
 * English: POJO representing BGE cosine similarity score and decision detail for an adjacent message pair.
 *
 * @author Antigravity
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationPairDetail {

    /**
     * Pair 序号 (1-indexed)
     */
    private int index;

    /**
     * 上文 Message A 角色 (user / assistant)
     */
    private String roleA;

    /**
     * 上文 Message A 文本内容
     */
    private String textA;

    /**
     * 下文 Message B 角色 (user / assistant)
     */
    private String roleB;

    /**
     * 下文 Message B 文本内容
     */
    private String textB;

    /**
     * BGE 余弦相似度得分
     */
    private double score;

    /**
     * 判定类型: strong (强相关 >= highThreshold), ambiguous (模糊区 lowThreshold <= score < highThreshold), split (切断 < lowThreshold)
     */
    private String type;

    /**
     * 诊断 UI 展示标签
     */
    private String label;
}
