package com.cresensolutions.leaveservice.dto.aidto;

import lombok.Data;

@Data
public class EditChatTitleRequest {
    private String chatId;
    private String newChatTitle;
}
