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
     * 兼容模式 API Base URL
     */
    private String baseUrl;

    /**
     * 默认纯文本大语言模型名称，如 qwen3.6-flash-2026-04-16
     */
    private String textModel = "qwen3.6-flash-2026-04-16";

    /**
     * 默认全模态大模型名称，如 qwen3-vl-plus
     */
    private String multimodalModel = "qwen3-vl-plus";

    /**
     * 旧版单模型名称（兼容字段）
     */
    private String modelName;

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

    /**
     * 获取有效的纯文本模型名称（优先读 textModel，退守 modelName，默认 qwen3.6-flash-2026-04-16）
     */
    public String getTextModel() {
        if (textModel != null && !textModel.isBlank()) {
            return textModel;
        }
        if (modelName != null && !modelName.isBlank()) {
            return modelName;
        }
        return "qwen3.6-flash-2026-04-16";
    }

    /**
     * 获取有效的多模态模型名称（优先读 multimodalModel，默认 qwen3-vl-plus）
     */
    public String getMultimodalModel() {
        if (multimodalModel != null && !multimodalModel.isBlank()) {
            return multimodalModel;
        }
        return "qwen3-vl-plus";
    }

    /**
     * 兼容旧版 getter 方法
     */
    public String getModelName() {
        return getTextModel();
    }
}
