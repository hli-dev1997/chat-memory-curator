package com.chatgpt.memory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 类说明 / Class Description:
 * 中文：Swagger / OpenAPI 3 接口文档自动配置类。
 * English: Swagger / OpenAPI 3 API documentation auto-configuration class.
 * <p>
 * 设计目的 / Design Purpose:
 * 中文：参考 quant-nano-alpha 微服务架构规范，集成 Knife4j / SpringDoc 提供在线可交互接口文档。
 * English: Following quant-nano-alpha microservice spec, integrating Knife4j / SpringDoc for interactive API docs.
 * </p>

 * @author Antigravity
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChatGPT 对话记忆库解析与导出一体化系统 API")
                        .description("ChatGPT Export Data Parser & Memory Base Exporter API Specification")
                        .version("1.0.0-SNAPSHOT")
                        .contact(new Contact()
                                .name("Antigravity Architect Team")
                                .email("architect@chatgpt.memory"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
