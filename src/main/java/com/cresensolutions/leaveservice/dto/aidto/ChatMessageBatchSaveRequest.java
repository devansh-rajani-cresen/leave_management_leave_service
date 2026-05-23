package com.cresensolutions.leaveservice.dto.aidto;

import lombok.Data;

import java.util.List;

@Data
public class ChatMessageBatchSaveRequest {
    private List<ChatMessageSaveRequest> messages;
}
