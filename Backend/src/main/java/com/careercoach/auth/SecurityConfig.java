package com.careercoach.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY security config — everything is open so the skeleton can run
 * without a complete OAuth flow.
 *
 * <p>TODO (TECHNICAL_DESIGN §7): Google OAuth2 ({@code oauth2Login}) +
 * email whitelist (access only for the author's address). At that point
 * {@code anyRequest} becomes {@code authenticated()}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
