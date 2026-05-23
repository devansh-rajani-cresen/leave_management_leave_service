package com.cresensolutions.leaveservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static com.cresensolutions.leaveservice.common.AIConfigConstants.*;

@RequiredArgsConstructor
@Slf4j
@Configuration
public class ChatClientConfig {

    // 1) Ollama API (connects to local Ollama)
    @Bean
    public OllamaApi ollamaApi() {
        return new OllamaApi(OLLAMA_BASE_URL);
    }

    // 2) Chat Model (LLM config -> Only do communication with LLM, no logic)
    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        log.info("[OLLAMA] - Connecting with OLLAMA!");
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(OLLAMA_MODEL_NAME)
                        .temperature(MODEL_TEMPERATURE)
                        .build())
                .build();
    }

    // 3) ChatClient
    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel) {
        return ChatClient
                .builder(chatModel)
                .build();
    }
}
