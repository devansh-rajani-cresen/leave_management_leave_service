package com.cresensolutions.leaveservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Objects;
import static com.cresensolutions.leaveservice.common.LeaveConstants.AUTH_HEADER;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            String token = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder
                    .getRequestAttributes()))
                    .getRequest()
                    .getHeader(AUTH_HEADER);

            if (token != null) {
                requestTemplate.header(AUTH_HEADER, token);
            }
        };
    }
}
