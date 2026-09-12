package com.fredinvest.dto;

import com.fredinvest.model.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public class AuthDTOs {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegisterRequest {
        @NotBlank
        private String fullName;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OAuth2LoginRequest {
        @NotBlank
        private String provider; // GOOGLE, APPLE, MICROSOFT

        @NotBlank
        private String token; // IdToken ou AccessToken fornecido pelo front

        private String email;
        private String fullName;
        private String profilePictureUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuthResponse {
        private String accessToken;
        private String tokenType;
        private Long userId;
        private String email;
        private String fullName;
        private AuthProvider provider;
    }
}

