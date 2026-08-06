package com.chatgpt.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：VectorSessionSplitter 对单个 ChatConversation 进行向量物理切分后的完整结果与诊断明细聚合实体。
 * English: Result aggregate containing split ChatSessions and pair BGE similarity details.
 *
 * @author Antigravity
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VectorSegmentationResult {

    /**
     * 切分生成的 Session 片段列表
     */
    private List<ChatSession> sessions;

    /**
     * 逐句 Pair 的 BGE 余弦得分与 Tag 判定明细列表
     */
    private List<ConversationPairDetail> pairDetails;
}
