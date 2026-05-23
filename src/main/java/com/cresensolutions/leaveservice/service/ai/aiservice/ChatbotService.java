package com.cresensolutions.leaveservice.service.ai.aiservice;

import com.cresensolutions.leaveservice.dto.aidto.ChatDTO;
import com.cresensolutions.leaveservice.dto.aidto.ChatMessageSaveRequest;
import com.cresensolutions.leaveservice.dto.aidto.MessageDTO;
import java.util.List;

public interface ChatbotService {
    String getResponse(String message, Long userId, String chatId);
    void saveChatMessages(Long userId, String chatId, String userMessage, String assistantMessage);
    void saveChatMessagesBatch(Long userId, List<ChatMessageSaveRequest> messages);
    void editChatTitle(Long userId, String chatId, String newChatTitle);
    void deleteChatHistory(Long userId, String chatId);
    List<ChatDTO> getAllChatTitles(Long userId);
    List<MessageDTO> getChatMessages(Long userId, String chatId);
}
