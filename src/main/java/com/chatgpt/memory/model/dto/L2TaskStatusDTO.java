package com.chatgpt.memory.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 类说明 / Class Description:
 * 中文：Stage 2 L2 大模型精排任务后台运行状态传输对象。
 * English: DTO for Stage 2 L2 model refinement task execution status.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：记录后台异步任务是否进行中、已处理数量、失败阻断记录 ID 及最后一次不可恢复的模型 API 报错信息，
 * 供前端工作台可视化监控与异常弹窗感知。
 * </p>
 *
 * @author Antigravity
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2TaskStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务是否正在后台运行中
     */
    private Boolean running;

    /**
     * 任务最后一次抛出的不可恢复的大模型报错详情（如 Free quota exhausted）
     */
    private String lastError;

    /**
     * 触发报错中断的 Pair 记录 ID
     */
    private Long failedPairId;

    /**
     * 本批次成功推导处理的 Pair 条数
     */
    private Integer processedCount;

    /**
     * 任务启动时间
     */
    private LocalDateTime startTime;

    /**
     * 任务完成或异常中断时间
     */
    private LocalDateTime finishTime;
}
