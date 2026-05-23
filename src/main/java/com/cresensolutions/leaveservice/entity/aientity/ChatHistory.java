package com.cresensolutions.leaveservice.entity.aientity;

import com.cresensolutions.leaveservice.dto.aidto.ChatDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "creni_ai_chat_history", schema = "core")
@Data
public class ChatHistory {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "profile")
    private String profile;

    @Column(name = "product_name")
    private String productName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chats")
    private List<ChatDTO> chats;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

}
