package com.chatgpt.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ChatGPT 导出的记忆库数据解析与提取应用主启动类
 * <p>
 * 本应用负责高效解析 ChatGPT 导出的 HTML 格式文件，
 * 提取采纳的主线对话分支并进行时间间隔（默认 4 小时）逻辑切分，
 * 为后续 Phase 2 的记忆库构建与 AI 价值评估提供干净的数据基础设施。
 * </p>
 *
 * @author Antigravity
 */
@Slf4j
@SpringBootApplication
public class MemoryExporterApplication {

    /**
     * 应用入口 main 方法
     *
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        SpringApplication.run(MemoryExporterApplication.class, args);
        log.info("MemoryExporterApplication started successfully.");
    }
}

