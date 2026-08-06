package com.chatgpt.memory.config;

import dev.langchain4j.model.embedding.BgeSmallZhEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量 Embedding 模型 Spring 上下文配置类
 * <p>
 * 将 BGE 中文小模型注册为 Spring 容器管理的单例 Bean，
 * 避免在计算向量相似度时多次构造模型对象引发 CPU、内存与磁盘 IO 开销。
 * </p>
 *
 * @author Antigravity
 */
@Configuration
public class EmbeddingConfig {

    /**
     * 注册 BGE 中文小模型单例 Bean
     *
     * @return BGE 向量模型实例
     */
    @Bean
    public EmbeddingModel bgeSmallZhEmbeddingModel() {
        return new BgeSmallZhEmbeddingModel();
    }
}
