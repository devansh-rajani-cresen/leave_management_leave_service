package com.cresensolutions.leaveservice.dto.aidto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String chatId;
}
