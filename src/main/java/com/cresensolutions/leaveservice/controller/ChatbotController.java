package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.aidto.ChatDTO;
import com.cresensolutions.leaveservice.dto.aidto.ChatMessageBatchSaveRequest;
import com.cresensolutions.leaveservice.dto.aidto.ChatMessageSaveRequest;
import com.cresensolutions.leaveservice.dto.aidto.ChatRequest;
import com.cresensolutions.leaveservice.dto.aidto.EditChatTitleRequest;
import com.cresensolutions.leaveservice.dto.aidto.MessageDTO;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.service.ai.aiservice.ChatbotService;
import com.cresensolutions.leaveservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/ai")
@AllArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final JwtUtil jwtUtil;

    // ASK Question to AI
    @PostMapping("/ollama/ask")
    public ResponseEntity<String> ask(@RequestBody ChatRequest chatRequest,
                                      HttpServletRequest request) {
        try {
            long start = System.currentTimeMillis();
            Long userId = extractUserId(request);
            String response = chatbotService.getResponse(
                    chatRequest.getMessage(),
                    userId,
                    chatRequest.getChatId()
            );
            log.info("AI Response Latency: {} ms", System.currentTimeMillis() - start);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI chatbot error", e);
            return ResponseEntity.internalServerError()
                    .body("Something went wrong while processing your request.");
        }
    }

    // GET All Chats Titles
    @GetMapping("/chat/titles")
    public ResponseEntity<List<ChatDTO>> getAllChatTitles(HttpServletRequest request){
        Long userId = extractUserId(request);
        return ResponseEntity.ok(chatbotService.getAllChatTitles(userId));

    }

    // GET messages of particular Chat
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<List<MessageDTO>> getChatMessages(@PathVariable String chatId,
                                                            HttpServletRequest request){
        Long userId = extractUserId(request);
        return ResponseEntity.ok(chatbotService.getChatMessages(userId, chatId));
    }

    @PostMapping("/chat/message/save")
    public ResponseEntity<String> saveChatMessages(@RequestBody ChatMessageSaveRequest saveRequest,
                                                   HttpServletRequest request) {
        Long userId = extractUserId(request);
        chatbotService.saveChatMessages(
                userId,
                saveRequest.getChatId(),
                saveRequest.getUserMessage(),
                saveRequest.getAssistantMessage()
        );
        return ResponseEntity.ok("Chat messages saved!");
    }

    // SAVE chat messages once (saving multiple API Calls)
    @PostMapping("/chat/messages/save-batch")
    public ResponseEntity<String> saveChatMessagesBatch(@RequestBody ChatMessageBatchSaveRequest saveRequest,
                                                        HttpServletRequest request) {
        Long userId = extractUserId(request);
        chatbotService.saveChatMessagesBatch(userId, saveRequest.getMessages());
        return ResponseEntity.ok("Chat messages saved!");
    }

    // EDIT Chat Title
    @PutMapping("/chat-title/update")
    public ResponseEntity<String> updateChatTitle(@RequestBody EditChatTitleRequest editChatTitleRequest,
                                                  HttpServletRequest request){
        String newChatTitle = editChatTitleRequest.getNewChatTitle();
        String chatId = editChatTitleRequest.getChatId();
        Long userId = extractUserId(request);
        chatbotService.editChatTitle(userId, chatId, newChatTitle);
        return ResponseEntity.ok("Chat Title Edited!");
    }

    // DELETE Chat History
    @DeleteMapping("/chat-title/delete/{chatId}")
    public ResponseEntity<String> deleteChat(@PathVariable String chatId,
                                             HttpServletRequest request){
        Long userId = extractUserId(request);
        chatbotService.deleteChatHistory(userId, chatId);
        return ResponseEntity.ok("Chat deleted!");
    }

    // HELPER : Extract userId from token
    private Long extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(HEADER_STARTING)) {
            throw new CustomException("Invalid or missing token",404);
        }
        String token = authHeader.substring(TOKEN_STARTING_INDEX);
        return jwtUtil.extractUserId(token);
    }
}
