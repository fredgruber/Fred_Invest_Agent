package com.fredinvest.config.oauth2;

import com.fredinvest.config.JwtTokenProvider;
import com.fredinvest.model.AuthProvider;
import com.fredinvest.model.User;
import com.fredinvest.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public OAuth2AuthenticationSuccessHandler(JwtTokenProvider tokenProvider,
                                             UserRepository userRepository,
                                             HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Resposta já foi enviada. Não é possível redirecionar para: {}", targetUrl);
            return;
        }

        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    @SuppressWarnings("null")
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oauthUser = oauthToken.getPrincipal();
        Map<String, Object> attributes = oauthUser.getAttributes();

        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(registrationId.toUpperCase());
        } catch (Exception e) {
            provider = AuthProvider.GOOGLE;
        }

        String email = null;
        String name = null;
        String picture = null;
        String providerId = oauthUser.getName();

        if (provider == AuthProvider.GOOGLE) {
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
            picture = (String) attributes.get("picture");
            providerId = (String) attributes.get("sub");
        } else if (provider == AuthProvider.MICROSOFT) {
            email = (String) attributes.get("mail");
            if (email == null || email.isBlank()) {
                email = (String) attributes.get("userPrincipalName");
            }
            name = (String) attributes.get("displayName");
            providerId = (String) attributes.get("id");
        } else if (provider == AuthProvider.APPLE) {
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
            providerId = (String) attributes.get("sub");
        }

        if (email == null || email.isBlank()) {
            email = registrationId.toLowerCase() + "_" + providerId + "@fredinvest.com";
        }
        if (name == null || name.isBlank()) {
            name = "Usuário " + provider;
        }

        final String finalEmail = email;
        final String finalName = name;
        final String finalPicture = picture;
        final String finalProviderId = providerId;
        final AuthProvider finalProvider = provider;

        final User user = userRepository.findByEmail(finalEmail)
                .map(existingUser -> {
                    existingUser.setFullName(finalName);
                    if (finalPicture != null) existingUser.setProfilePictureUrl(finalPicture);
                    existingUser.setProvider(finalProvider);
                    existingUser.setProviderId(finalProviderId);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    final User newUser = User.builder()
                            .email(finalEmail)
                            .fullName(finalName)
                            .provider(finalProvider)
                            .providerId(finalProviderId)
                            .profilePictureUrl(finalPicture)
                            .build();
                    return userRepository.save(newUser);
                });

        String jwtToken = tokenProvider.generateTokenFromUsername(user.getEmail());
        log.info("Usuário autenticado via OAuth2 ({}) com sucesso: {}", provider, user.getEmail());

        return "/?token=" + URLEncoder.encode(jwtToken, StandardCharsets.UTF_8);
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}

