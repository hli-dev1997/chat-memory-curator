package com.chatgpt.memory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 静态资源与多模态文件下载访问配置
 * <p>
 * 将本地 ChatGPT 导出资产路径 e:/data/chatGPT_back/chatGPT导出20251214/ 映射为 /chat-assets/** HTTP 端点，
 * 支持极速复核工作台在前端安全访问显示图片、日志、PDF 与源码附件。
 * </p>
 *
 * @author Antigravity
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 导出物理资产本地基准路径
     */
    private static final String REAL_DATA_ASSETS_DIR = "file:e:/data/chatGPT_back/chatGPT导出20251214/";

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/chat-assets/**")
                .addResourceLocations(REAL_DATA_ASSETS_DIR);
    }
}
