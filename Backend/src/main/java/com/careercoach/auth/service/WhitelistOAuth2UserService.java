package com.careercoach.auth.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.careercoach.auth.domain.EmailWhitelist;
import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.gamification.service.CareerProfileService;

public class WhitelistOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final CareerProfileService careerProfileService;
    private final EmailWhitelist emailWhitelist;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    public WhitelistOAuth2UserService(UserRepository userRepository,
                                      CareerProfileService careerProfileService,
                                      EmailWhitelist emailWhitelist) {
        this(userRepository, careerProfileService, emailWhitelist, new DefaultOAuth2UserService());
    }

    // Test seam: inject the delegate that loads the raw Google principal.
    WhitelistOAuth2UserService(UserRepository userRepository,
                               CareerProfileService careerProfileService,
                               EmailWhitelist emailWhitelist,
                               OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.userRepository = userRepository;
        this.careerProfileService = careerProfileService;
        this.emailWhitelist = emailWhitelist;
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        validateUser(oauth2User);
        User user = findOrCreateUser(oauth2User);
        careerProfileService.getOrCreate(user.getId());
        return oauth2User;
    }

    private void validateUser(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (!emailWhitelist.permits(email)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("access_denied", "Email not on whitelist", null),
                    "Email not on whitelist: " + email);
        }
    }

    private User findOrCreateUser(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        String googleSub = oauth2User.getAttribute("sub");
        String displayName = oauth2User.getAttribute("name");
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(email, googleSub, displayName)));
    }
}
