package com.cresensolutions.leaveservice.service.impl.aiimpl;

import com.cresensolutions.leaveservice.dto.aidto.ChatDTO;
import com.cresensolutions.leaveservice.dto.aidto.ChatMessageSaveRequest;
import com.cresensolutions.leaveservice.dto.aidto.MessageDTO;
import com.cresensolutions.leaveservice.dto.aidto.TablePayloadDTO;
import com.cresensolutions.leaveservice.entity.aientity.ChatHistory;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.airepository.ChatHistoryRepository;
import com.cresensolutions.leaveservice.security.UserPrincipal;
import com.cresensolutions.leaveservice.service.ai.aiservice.ChatCacheService;
import com.cresensolutions.leaveservice.service.ai.aiservice.ChatbotService;
import com.cresensolutions.leaveservice.service.ai.aiservice.LeaveAIToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static com.cresensolutions.leaveservice.common.AIConfigConstants.*;
import static com.cresensolutions.leaveservice.common.LeaveConstants.*;
import static com.cresensolutions.leaveservice.common.PromptConstants.*;
import static org.apache.commons.lang3.CharSetUtils.containsAny;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final ChatClient chatClient;
    private final LeaveAIToolService leaveAIToolService;
    private final ChatCacheService chatCacheService;
    private final ChatHistoryRepository chatHistoryRepository;

    // GET AI Response
    @Override
    public String getResponse(String message, Long userId, String chatId) {

        // Check for common greetings first
        if (isCommonGreeting(message)) {
            return GREETING_RESPONSE;
        }

        UserPrincipal principal = (UserPrincipal) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        String role = principal.getRole();

        // 1. check in Cache, if found then return
        String cacheKey = userId + ":" + role + ":" + message.toLowerCase().trim();
        String cached = chatCacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. If not found in cache, then check that if it is Common Query (Approved/Rejected/Pending)
        try {
            String deterministicResponse =
                    leaveAIToolService.tryHandleDeterministicQuery(message, role);

            if (deterministicResponse != null) {
                return cacheAndReturn(cacheKey, deterministicResponse);
            }

            // 3. Fast security shortcut for obvious org-level queries
            String normalized =
                    message.toLowerCase(Locale.ROOT);

            boolean definitelyOrgLevel =
                    containsAny(
                            normalized,
                            "organization",
                            "company",
                            "all employees",
                            "employee count",
                            "total employees",
                            "all managers"
                    );

            if (definitelyOrgLevel && !ROLE_ADMIN.equalsIgnoreCase(role)) {
                log.info(
                        "[FAST ACCESS BLOCK] role={} | question={}",
                        role,
                        message
                );
                return cacheAndReturn(
                        cacheKey,
                        "Sorry, you are not allowed to access this information."
                );
            }

            // 4. AI-based semantic access classification
            String accessDenied = checkRoleAccess(message, role);

            if (accessDenied != null) {
                return cacheAndReturn(cacheKey, accessDenied);
            }

            Object[] tools = leaveAIToolService.getToolsForRole(role);

            String response = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT.replace("{role}", role).replace("{userId}", String.valueOf(userId)))
                    .user(message)
                    .tools(tools)
                    .call()
                    .content();

            if (leaveAIToolService.isRawToolCallResponse(response)) {
                log.warn("Raw tool call leaked into final response for userId {}: {}", userId, response);
                response = "I could not format that result correctly. Please try asking for approved leave count, leave balance, or leave history.";
            }

            return cacheAndReturn(cacheKey, response);

        } catch (Exception e) {
            log.error("Error in getResponse", e);
            return "Something went wrong!";
        }
    }

    private String cacheAndReturn(String cacheKey, String response) {
        chatCacheService.put(cacheKey, response);
        return response;
    }

    @Override
    public void saveChatMessages(Long userId, String chatId, String userMessage, String assistantMessage) {
        try {
            saveChatMessagesBatch(userId, List.of(buildSaveRequest(chatId, userMessage, assistantMessage)));
        } catch (Exception e) {
            log.error("Error saving manual chat messages", e);
            throw new CustomException("Failed to save chat messages", 500);
        }
    }

    @Override
    public void saveChatMessagesBatch(Long userId, List<ChatMessageSaveRequest> messages) {
        try {
            if (messages == null || messages.isEmpty()) {
                return;
            }

            ChatHistory entity = chatHistoryRepository.findById(userId).orElse(null);

            List<ChatDTO> chats = (entity != null && entity.getChats() != null)
                    ? entity.getChats()
                    : new ArrayList<>();

            for (ChatMessageSaveRequest saveRequest : messages) {
                if (saveRequest == null
                        || saveRequest.getChatId() == null
                        || saveRequest.getChatId().isBlank()
                        || saveRequest.getUserMessage() == null
                        || saveRequest.getAssistantMessage() == null) {
                    continue;
                }

                ChatDTO chat = chats.stream()
                        .filter(c -> c.getChatId().equals(saveRequest.getChatId()))
                        .findFirst()
                        .orElse(null);

                if (chat == null) {
                    chat = new ChatDTO();
                    chat.setChatId(saveRequest.getChatId());
                    chat.setTitle(hasText(saveRequest.getChatTitle()) ? saveRequest.getChatTitle().trim() : INITIAL_TITLE);
                    chat.setPinned(false);
                    chat.setMessages(new ArrayList<>());
                    chats.add(chat);
                } else if (hasText(saveRequest.getChatTitle())) {
                    chat.setTitle(saveRequest.getChatTitle().trim());
                }

                if (chat.getMessages() == null) {
                    chat.setMessages(new ArrayList<>());
                }

                int nextSeq = chat.getMessages().size() + 1;
                chat.getMessages().add(buildTextMessage(nextSeq, saveRequest.getUserMessage()));
                applyFirstMessageTitleIfNeeded(chat, saveRequest.getUserMessage());
                chat.getMessages().add(buildAssistantMessage(nextSeq + 1, saveRequest.getAssistantMessage()));
            }

            UserPrincipal principal = (UserPrincipal) SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getPrincipal();

            persistChatHistory(entity, chats, userId, principal.getRole());
        } catch (Exception e) {
            log.error("Error saving manual chat messages batch", e);
            throw new CustomException("Failed to save chat messages", 500);
        }
    }

    private void persistChatHistory(ChatHistory entity, List<ChatDTO> chats, Long userId, String role) {
        if (entity == null) {
            entity = new ChatHistory();
            entity.setUserId(userId);
            entity.setProfile(role);
            entity.setProductName(PRODUCT_NAME);
        }

        entity.setChats(chats);
        OffsetDateTime now = OffsetDateTime.now();

        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        chatHistoryRepository.save(entity);
    }

    private void applyFirstMessageTitleIfNeeded(ChatDTO chat, String message) {
        if (chat == null || message == null || message.isBlank()) {
            return;
        }

        boolean isNewChatTitle = chat.getTitle() == null || chat.getTitle().isBlank() || INITIAL_TITLE.equals(chat.getTitle());
        boolean isFirstUserMessage = chat.getMessages() != null && chat.getMessages().size() == 1;

        if (!isNewChatTitle || !isFirstUserMessage) {
            return;
        }

        chat.setTitle(buildTitleFromMessage(message));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildTitleFromMessage(String message) {
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 45) {
            return normalized;
        }
        return normalized.substring(0, 42).trim() + "...";
    }

    private MessageDTO buildTextMessage(int seq, String content) {
        return new MessageDTO(seq, USER_ROLE, content, java.time.Instant.now().toString(), "TEXT", null);
    }

    private ChatMessageSaveRequest buildSaveRequest(String chatId, String userMessage, String assistantMessage) {
        ChatMessageSaveRequest request = new ChatMessageSaveRequest();
        request.setChatId(chatId);
        request.setUserMessage(userMessage);
        request.setAssistantMessage(assistantMessage);
        return request;
    }

    private MessageDTO buildAssistantMessage(int seq, String content) {
        TablePayloadDTO tablePayload = leaveAIToolService.buildStructuredTablePayload(content);
        if (tablePayload != null) {
            return new MessageDTO(seq, ASSISTANT_ROLE, content, java.time.Instant.now().toString(), "TABLE", tablePayload);
        }
        return new MessageDTO(seq, ASSISTANT_ROLE, content, java.time.Instant.now().toString(), "TEXT", null);
    }

    // GET All Chat Titles
    @Override
    public List<ChatDTO> getAllChatTitles(Long userId) {
        try{
            ChatHistory entity = chatHistoryRepository.findById(userId).orElse(null);

            if (entity == null || entity.getChats() == null){
                return new ArrayList<>();
            }

            List<ChatDTO> chats = entity.getChats();

            return chats.stream()
                    .map(chat -> new ChatDTO(
                            chat.getChatId(),
                            chat.getTitle(),
                            chat.isPinned(),
                            null
                    )).toList();
        } catch (Exception e) {
            log.error("Error fetching chat titles", e);
            throw new CustomException("Failed to fetch Chat Titles", 500);
        }
    }

    // GET Chat Messages of particular Chat
    @Override
    public List<MessageDTO> getChatMessages(Long userId, String chatId) {
        ChatHistory entity = chatHistoryRepository.findById(userId)
                .orElseThrow(() -> new CustomException("Chat history not found!", 404));

        List<ChatDTO> chats = entity.getChats();

        ChatDTO chat = chats.stream()
                .filter(c -> c.getChatId().equals(chatId))
                .findFirst()
                .orElseThrow(() -> new CustomException("Chat not found!", 404));

        return chat.getMessages();
    }

    // EDIT Chat Title
    @Override
    public void editChatTitle(Long userId, String chatId, String newChatTitle) {

        try {
            ChatHistory entity = chatHistoryRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Chat history not found"));

            List<ChatDTO> chats = entity.getChats() != null
                    ? entity.getChats()
                    : new ArrayList<>();

            for (ChatDTO chat : chats) {
                if (chat.getChatId().equals(chatId)) {
                    chat.setTitle(newChatTitle);
                    break;
                }
            }

            entity.setChats(chats);
            chatHistoryRepository.save(entity);

        } catch (Exception e) {
            log.error("Error updating chat title", e);
            throw new CustomException("Failed to update chat title", 500);
        }
    }

    // DELETE Chat History
    @Override
    public void deleteChatHistory(Long userId, String chatId) {

        try {
            ChatHistory entity = chatHistoryRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Chat history not found"));

            List<ChatDTO> chats = entity.getChats() != null
                    ? entity.getChats()
                    : new ArrayList<>();

            chats.removeIf(chat -> chat.getChatId().equals(chatId));

            entity.setChats(chats);
            chatHistoryRepository.save(entity);

        } catch (Exception e) {
            log.error("Error deleting chat", e);
            throw new CustomException("Failed to delete chat", 500);
        }
    }

    // COMMON GREETINGS response instead of calling AI
    private boolean isCommonGreeting(String message) {
        if (message == null) {
            return false;
        }

        String normalizedMessage = message
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z]", "");

        return COMMON_GREETINGS.contains(normalizedMessage);
    }

    private String checkRoleAccess(String message, String role) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalizedRole =
                role == null ? "" : role.toUpperCase(Locale.ROOT);

        // ADMIN can access everything
        if (ROLE_ADMIN.equals(normalizedRole)) {
            return null;
        }
        Set<String> accessibleRoles =
                classifyAccessibleRoles(message);
        if (isAccessDeniedByClassification(
                normalizedRole,
                accessibleRoles
        )) {
            log.info(
                    "[ACCESS BLOCKED] currentRole={} | allowedRoles={} | question={}",
                    normalizedRole,
                    accessibleRoles,
                    message
            );
            return "Sorry, you are not allowed to access this information.";
        }
        return null;
    }

    private Set<String> classifyAccessibleRoles(String message) {
        try {
            log.info("Classifying Accessible Roles!");
            String response = chatClient
                    .prompt()
                    .system(ACCESS_CLASSIFIER_PROMPT)
                    .user(message)
                    .call()
                    .content();

            log.info("Role decided by AI: {}", response);

            if (response == null || response.isBlank()) {
                return Set.of();
            }
            Set<String> roles = new LinkedHashSet<>();
            for (String token : response.toUpperCase(Locale.ROOT).split("[,\\s]+")) {
                String normalized = token.replaceAll("[^A-Z_]", "");
                if (ROLE_EMPLOYEE.equals(normalized) || ROLE_MANAGER.equals(normalized) || ROLE_ADMIN.equals(normalized)) {
                    roles.add(normalized);
                }
            }
            return roles;
        } catch (Exception e) {
            log.warn("Failed to classify access for message: {}", message, e);
            return Set.of();
        }
    }

    private boolean isAccessDeniedByClassification(String currentRole, Set<String> accessibleRoles) {
        if (accessibleRoles == null || accessibleRoles.isEmpty()) {
            return false;
        }
        return !accessibleRoles.contains(currentRole);
    }
}
