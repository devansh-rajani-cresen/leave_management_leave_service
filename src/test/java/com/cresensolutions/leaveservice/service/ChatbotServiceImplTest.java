package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.aidto.ChatDTO;
import com.cresensolutions.leaveservice.dto.aidto.ChatMessageSaveRequest;
import com.cresensolutions.leaveservice.dto.aidto.MessageDTO;
import com.cresensolutions.leaveservice.dto.aidto.TablePayloadDTO;
import com.cresensolutions.leaveservice.entity.aientity.ChatHistory;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.airepository.ChatHistoryRepository;
import com.cresensolutions.leaveservice.security.UserPrincipal;
import com.cresensolutions.leaveservice.service.ai.aiservice.ChatCacheService;
import com.cresensolutions.leaveservice.service.ai.aiservice.LeaveAIToolService;
import com.cresensolutions.leaveservice.service.impl.aiimpl.ChatbotServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private LeaveAIToolService leaveAIToolService;

    @Mock
    private ChatCacheService chatCacheService;

    @Mock
    private ChatHistoryRepository chatHistoryRepository;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ChatbotServiceImpl service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurity(String role) {

        UserPrincipal principal = mock(UserPrincipal.class);

        when(principal.getRole()).thenReturn(role);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void mockAiFlow(String response) {

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(Collections.singletonList(any()))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(response);
    }

    @Test
    void getResponse_commonGreeting() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        String response =
                service.getResponse("hello", 1L, "chat1");

        assertTrue(response.contains("Welcome"));

        verifyNoInteractions(chatClient);
    }

    @Test
    void getResponse_cacheHit() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenReturn("cached");

        String response =
                service.getResponse("leave balance", 1L, "chat1");

        assertEquals("cached", response);
    }

    @Test
    void getResponse_deterministicResponse() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenReturn(null);

        when(leaveAIToolService.tryHandleDeterministicQuery(anyString(), anyString()))
                .thenReturn("5 leaves");

        String response =
                service.getResponse("leave balance", 1L, "chat1");

        assertEquals("5 leaves", response);

        verify(chatCacheService).put(anyString(), eq("5 leaves"));
    }

    @Test
    void getResponse_fastAccessBlocked() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenReturn(null);

        when(leaveAIToolService.tryHandleDeterministicQuery(anyString(), anyString()))
                .thenReturn(null);

        String response =
                service.getResponse("total employees in company", 1L, "chat1");

        assertTrue(response.contains("not allowed"));
    }

    @Test
    void getResponse_aiClassificationBlocked() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenReturn(null);

        when(leaveAIToolService.tryHandleDeterministicQuery(anyString(), anyString()))
                .thenReturn(null);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("ADMIN");

        String response =
                service.getResponse("show salary data", 1L, "chat1");

        assertTrue(response.contains("not allowed"));
    }

    @Test
    void getResponse_adminAllowed() {

        setupSecurity("ADMIN");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenReturn(null);

        when(leaveAIToolService.tryHandleDeterministicQuery(anyString(), anyString()))
                .thenReturn(null);

        when(leaveAIToolService.getToolsForRole(anyString()))
                .thenReturn(new Object[]{});

        mockAiFlow("AI Response");

        String response =
                service.getResponse("show all employees", 1L, "chat1");

        assertEquals("AI Response", response);
    }

    @Test
    void getResponse_rawToolCallResponse() {

        setupSecurity("ADMIN");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenReturn(null);

        when(leaveAIToolService.tryHandleDeterministicQuery(anyString(), anyString()))
                .thenReturn(null);

        when(leaveAIToolService.getToolsForRole(anyString()))
                .thenReturn(new Object[]{});

        when(leaveAIToolService.isRawToolCallResponse(anyString()))
                .thenReturn(true);

        mockAiFlow("RAW");

        String response =
                service.getResponse("query", 1L, "chat1");

        assertTrue(response.contains("format"));
    }

    @Test
    void getResponse_exception() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatCacheService.get(anyString()))
                .thenThrow(new RuntimeException());

        String response =
                service.getResponse("query", 1L, "chat1");

        assertEquals("Sorry, you are not allowed to access this information.", response);
    }

    @Test
    void saveChatMessages_success() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.empty());

        service.saveChatMessages(
                1L,
                "chat1",
                "hello",
                "hi"
        );

        verify(chatHistoryRepository).save(any());
    }

    @Test
    void saveChatMessagesBatch_empty() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        service.saveChatMessagesBatch(1L, List.of());

        verifyNoInteractions(chatHistoryRepository);
    }

    @Test
    void saveChatMessagesBatch_existingChat() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        ChatDTO chat = new ChatDTO();
        chat.setChatId("chat1");
        chat.setTitle("Old");
        chat.setMessages(new ArrayList<>());

        ChatHistory history = new ChatHistory();
        history.setChats(List.of(chat));

        ChatMessageSaveRequest request =
                new ChatMessageSaveRequest();

        request.setChatId("chat1");
        request.setChatTitle("New Title");
        request.setUserMessage("hello");
        request.setAssistantMessage("hi");

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.of(history));

        service.saveChatMessagesBatch(1L, List.of(request));

        verify(chatHistoryRepository).save(any());
    }

    @Test
    void saveChatMessagesBatch_invalidRequestSkipped() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        ChatMessageSaveRequest request =
                new ChatMessageSaveRequest();

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.empty());

        service.saveChatMessagesBatch(1L, List.of(request));

        verify(chatHistoryRepository).save(any());
    }

    @Test
    void saveChatMessagesBatch_tablePayload() {

        setupSecurity("EMPLOYEE");

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        TablePayloadDTO payload = new TablePayloadDTO();

        when(leaveAIToolService.buildStructuredTablePayload(anyString()))
                .thenReturn(payload);

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.empty());

        ChatMessageSaveRequest request =
                new ChatMessageSaveRequest();

        request.setChatId("chat1");
        request.setUserMessage("hello");
        request.setAssistantMessage("table");

        service.saveChatMessagesBatch(1L, List.of(request));

        verify(chatHistoryRepository).save(any());
    }

    @Test
    void getAllChatTitles_success() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        ChatDTO chat = new ChatDTO();
        chat.setChatId("chat1");
        chat.setTitle("Title");

        ChatHistory history = new ChatHistory();
        history.setChats(List.of(chat));

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.of(history));

        List<ChatDTO> response =
                service.getAllChatTitles(1L);

        assertEquals(1, response.size());
    }

    @Test
    void getAllChatTitles_empty() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.empty());

        List<ChatDTO> response =
                service.getAllChatTitles(1L);

        assertTrue(response.isEmpty());
    }

    @Test
    void getChatMessages_success() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        MessageDTO message =
                new MessageDTO();

        ChatDTO chat = new ChatDTO();
        chat.setChatId("chat1");
        chat.setMessages(List.of(message));

        ChatHistory history = new ChatHistory();
        history.setChats(List.of(chat));

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.of(history));

        List<MessageDTO> response =
                service.getChatMessages(1L, "chat1");

        assertEquals(1, response.size());
    }

    @Test
    void getChatMessages_historyNotFound() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                CustomException.class,
                () -> service.getChatMessages(1L, "chat1")
        );
    }

    @Test
    void getChatMessages_chatNotFound() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        ChatHistory history = new ChatHistory();
        history.setChats(List.of());

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.of(history));

        assertThrows(
                CustomException.class,
                () -> service.getChatMessages(1L, "chat1")
        );
    }

    @Test
    void editChatTitle_success() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        ChatDTO chat = new ChatDTO();
        chat.setChatId("chat1");

        ChatHistory history = new ChatHistory();
        history.setChats(new ArrayList<>(List.of(chat)));

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.of(history));

        service.editChatTitle(1L, "chat1", "New");

        verify(chatHistoryRepository).save(any());
    }

    @Test
    void deleteChatHistory_success() {

        service = new ChatbotServiceImpl(
                chatClient,
                leaveAIToolService,
                chatCacheService,
                chatHistoryRepository
        );

        ChatDTO chat = new ChatDTO();
        chat.setChatId("chat1");

        ChatHistory history = new ChatHistory();
        history.setChats(new ArrayList<>(List.of(chat)));

        when(chatHistoryRepository.findById(1L))
                .thenReturn(Optional.of(history));

        service.deleteChatHistory(1L, "chat1");

        verify(chatHistoryRepository).save(any());
    }
}