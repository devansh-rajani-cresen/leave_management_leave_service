package com.cresensolutions.leaveservice.service.ai.impl;

import com.cresensolutions.leaveservice.service.ai.aiservice.ChatbotService;
import com.cresensolutions.leaveservice.service.ai.aiservice.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;

    @Override
    public String getResponse(String message, Long userId) {
        log.info("Message: {} from userId: {}", message, userId);
        return chatClient.prompt(promptBuilder.buildPrompt(message, userId))
                .call()
                .content();
    }

}
