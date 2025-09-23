// src/main/java/com/example/finance/assistantservice/config/LlmConfig.java
package com.example.finance.assistantservice.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean
    ChatLanguageModel chatModel(
            @Value("${openai.apiKey}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.2)
                .build();
    }
}
