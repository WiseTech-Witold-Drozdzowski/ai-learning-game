package com.careercoach.auth.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import com.careercoach.auth.service.WhitelistOAuth2UserService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
            ObjectProvider<WhitelistOAuth2UserService> oauth2UserService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/ping", "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable());

        // oauth2Login is wired only when a Google client is configured, so the app still boots without OAuth credentials.
        if (clientRegistrationRepository.getIfAvailable() != null) {
            WhitelistOAuth2UserService userService = oauth2UserService.getIfAvailable();
            http.oauth2Login(oauth -> {
                if (userService != null) {
                    oauth.userInfoEndpoint(userInfo -> userInfo.userService(userService));
                }
            });
        }

        return http.build();
    }
}
