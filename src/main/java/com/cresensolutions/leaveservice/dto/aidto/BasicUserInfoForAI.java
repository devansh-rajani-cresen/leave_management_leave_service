package com.cresensolutions.leaveservice.dto.aidto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BasicUserInfoForAI {
    private Long userId;
    private String fullName;
    private String role;
    private String email;
}
