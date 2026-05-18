package com.cresensolutions.leaveservice.util;

import com.cresensolutions.leaveservice.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Component
@RequiredArgsConstructor
public class JwtRequestUtil {
    public String extractToken(HttpServletRequest request){
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(HEADER_STARTING)){
            throw new CustomException("Invalid Token!", 400);
        }
        return authHeader.substring(TOKEN_STARTING_INDEX);
    }
}
