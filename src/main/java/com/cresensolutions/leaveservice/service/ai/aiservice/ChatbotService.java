package com.cresensolutions.leaveservice.service.ai;

public interface ChatbotService {
    String getResponse(String message, Long userId);
}
