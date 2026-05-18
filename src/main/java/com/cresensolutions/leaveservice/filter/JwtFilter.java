package com.cresensolutions.leaveservice.filter;

import com.cresensolutions.leaveservice.security.UserPrincipal;
import com.cresensolutions.leaveservice.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Skip JWT validation for public endpoints
        if (path.startsWith("/auth")
                || path.startsWith("/actuator")) {

            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTH_HEADER);

        String token = null;

        if (authHeader != null && authHeader.startsWith(HEADER_STARTING)) {
            token = authHeader.substring(TOKEN_STARTING_INDEX);
        }

        try {

            // Validate token first
            if (token != null
                    && jwtUtil.validateToken(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Extract user details from token
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);
                Long userId = jwtUtil.extractUserId(token);

                // Create custom principal object
                UserPrincipal userPrincipal =
                        new UserPrincipal(userId, username, role);

                // Create Spring Security auth object
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                // Save authentication in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception e) {

            log.error("JWT Error: {}", e.getMessage());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or Expired JWT Token");
            return;
        }

        // Continue request flow
        filterChain.doFilter(request, response);
    }
}