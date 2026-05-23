package com.cresensolutions.leaveservice.security.aspect;

import com.cresensolutions.leaveservice.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import static com.cresensolutions.leaveservice.common.LeaveConstants.ROLE_ADMIN;

@Aspect
@Component
@Slf4j
public class RoleChekAspect {
    @Around("@annotation(com.cresensolutions.leaveservice.security.annotation.AdminOnly)")
    public Object checkAdminAccess(ProceedingJoinPoint joinPoint) throws Throwable {

        UserPrincipal user = (UserPrincipal) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        log.info("AOP Hit");

        if (!ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            log.info("[AOP] - ROLE is not of ADMIN!");
            return "Sorry, You are not authorized to access this information! Please ask questions relevant to your role.";
        }

        log.info("[AOP] - ADMIN Role & Calling according method");
        return joinPoint.proceed();
    }

}
