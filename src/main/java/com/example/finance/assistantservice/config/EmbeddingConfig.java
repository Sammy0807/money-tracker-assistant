// src/main/java/com/example/finance/assistantservice/config/EmbeddingConfig.java
package com.example.finance.assistantservice.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    EmbeddingModel embeddingModel(
            @Value("${openai.apiKey}") String apiKey,
            @Value("${openai.embeddingModel:text-embedding-3-small}") String model
    ) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }
}
