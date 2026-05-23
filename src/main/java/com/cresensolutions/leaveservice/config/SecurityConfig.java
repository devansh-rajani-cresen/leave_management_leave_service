package com.cresensolutions.leaveservice.config;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.filter.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/manager/**").hasRole(LeaveConstants.ROLE_MANAGER)
                        .requestMatchers(
                                "/leaves/create-leave",
                                "/leaves/update-leave",
                                "/leaves/delete-leave/**",
                                "/leaves/create-public-holiday",
                                "/leaves/update-public-holiday/**",
                                "/leaves/delete-public-holiday/**",
                                "/hr/**"
                        ).hasRole(LeaveConstants.ROLE_ADMIN)
                        .requestMatchers(
                                "/leaves/get-leaves",
                                "/leaves/apply-leave",
                                "/leaves/my-leaves/**",
                                "/leaves/public-holidays",
                                "/leaves/leave-balance",
                                "/ai/**"
                        ).hasAnyRole(
                                LeaveConstants.ROLE_ADMIN,
                                LeaveConstants.ROLE_MANAGER,
                                LeaveConstants.ROLE_EMPLOYEE
                        )
                        .requestMatchers("/leaves/create-employee-leave").hasAnyRole(
                                LeaveConstants.ROLE_ADMIN,
                                LeaveConstants.ROLE_MANAGER,
                                LeaveConstants.ROLE_HR,
                                LeaveConstants.ROLE_EMPLOYEE
                        )
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
