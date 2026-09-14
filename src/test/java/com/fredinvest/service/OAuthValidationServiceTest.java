package com.fredinvest.service;

import com.fredinvest.model.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthValidationServiceTest {

    private OAuthValidationService oAuthValidationService;

    @BeforeEach
    void setUp() {
        oAuthValidationService = new OAuthValidationService();
    }

    @Test
    void validateAndExtractProfileRejectsNullOrBlankToken() {
        assertThrows(IllegalArgumentException.class, () ->
                oAuthValidationService.validateAndExtractProfile(AuthProvider.GOOGLE, null));

        assertThrows(IllegalArgumentException.class, () ->
                oAuthValidationService.validateAndExtractProfile(AuthProvider.MICROSOFT, "   "));
    }

    @Test
    void validateAndExtractProfileRejectsLocalProvider() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                oAuthValidationService.validateAndExtractProfile(AuthProvider.LOCAL, "some-token"));

        assertTrue(exception.getMessage().contains("Provedor não suportado para validação direta"));
    }

    @Test
    void validateAndExtractProfileRejectsInvalidTokenAgainstGoogleApi() {
        // Token falso que a API do Google rejeita
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                oAuthValidationService.validateAndExtractProfile(AuthProvider.GOOGLE, "mock_token_invalido_12345"));

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().toLowerCase().contains("google"));
    }

    @Test
    void validateAndExtractProfileRejectsInvalidTokenAgainstMicrosoftApi() {
        // Token falso que a API da Microsoft rejeita
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                oAuthValidationService.validateAndExtractProfile(AuthProvider.MICROSOFT, "mock_token_invalido_12345"));

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().toLowerCase().contains("microsoft"));
    }

    @Test
    void validateAndExtractProfileRejectsMalformedAppleJwt() {
        // Token com formato inválido para JWT
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                oAuthValidationService.validateAndExtractProfile(AuthProvider.APPLE, "mock_token_invalido_12345"));

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Token Apple inválido"));
    }
}

