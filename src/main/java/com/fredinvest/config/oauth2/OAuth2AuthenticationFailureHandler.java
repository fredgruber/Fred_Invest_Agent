package com.fredinvest.config.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public OAuth2AuthenticationFailureHandler(HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository) {
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        log.error("Falha na autenticação OAuth2: {}", exception.getMessage());

        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

        String targetUrl = "/?error=" + URLEncoder.encode(exception.getLocalizedMessage() != null ? exception.getLocalizedMessage() : "Falha na autenticação com o provedor", StandardCharsets.UTF_8);

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

