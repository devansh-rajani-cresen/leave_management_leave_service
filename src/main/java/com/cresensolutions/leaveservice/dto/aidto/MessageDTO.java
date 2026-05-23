// DTO for single message
package com.cresensolutions.leaveservice.dto.aidto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MessageDTO {
    private int seq;
    private String role;
    private String content;
    private String timestamp;
    private String contentType;
    private TablePayloadDTO tableData;

    public MessageDTO(int seq, String role, String content, String timestamp) {
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.contentType = "TEXT";
    }

    public MessageDTO(int seq, String role, String content, String timestamp, String contentType, TablePayloadDTO tableData) {
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.contentType = contentType;
        this.tableData = tableData;
    }
}
