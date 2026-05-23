// DTO for One Conversation
package com.cresensolutions.leaveservice.dto.aidto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatDTO {
    private String chatId;
    private String title;
    private boolean isPinned;
    private List<MessageDTO> messages;
}