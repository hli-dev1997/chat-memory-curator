package com.chatgpt.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通义千问 (Qwen) 大语言模型配置属性实体类
 * <p>
 * 映射配置文件中 prefix = "qwen" 的属性，包含 API Key、模型名称及端点地址等。
 * </p>
 *
 * @author Antigravity
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qwen")
public class QwenProperties {

    /**
     * DashScope 接口密钥
     */
    private String apiKey;

    /**
     * 模型名称，如 qwen3.6-flash
     */
    private String modelName;

    /**
     * 兼容模式 API Base URL
     */
    private String baseUrl;

    /**
     * 请求超时时间 (单位：秒)
     */
    private Integer timeoutSeconds;

    /**
     * 是否开启请求日志记录
     */
    private Boolean logRequests;

    /**
     * 是否开启响应日志记录
     */
    private Boolean logResponses;
}
