package com.cresensolutions.leaveservice.dto.aidto;

import lombok.Data;

@Data
public class ChatMessageSaveRequest {
    private String chatId;
    private String chatTitle;
    private String userMessage;
    private String assistantMessage;
}
