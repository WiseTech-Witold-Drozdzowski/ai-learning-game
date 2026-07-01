package com.careercoach.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.careercoach.auth.domain.EmailWhitelist;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.auth.service.WhitelistOAuth2UserService;
import com.careercoach.gamification.service.CareerProfileService;

@Configuration
@EnableConfigurationProperties(EmailWhitelist.class)
public class AuthConfig {

    @Bean
    public WhitelistOAuth2UserService whitelistOAuth2UserService(
            UserRepository userRepository,
            CareerProfileService careerProfileService,
            EmailWhitelist emailWhitelist) {
        return new WhitelistOAuth2UserService(userRepository, careerProfileService, emailWhitelist);
    }
}
