package com.fredinvest.dto;

import com.fredinvest.model.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDTOs {

    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;

        public LoginRequest() {}
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public static LoginRequestBuilder builder() { return new LoginRequestBuilder(); }
        public static class LoginRequestBuilder {
            private String email;
            private String password;
            public LoginRequestBuilder email(String email) { this.email = email; return this; }
            public LoginRequestBuilder password(String password) { this.password = password; return this; }
            public LoginRequest build() { return new LoginRequest(email, password); }
        }
    }

    public static class RegisterRequest {
        @NotBlank
        private String fullName;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
        private String password;

        public RegisterRequest() {}
        public RegisterRequest(String fullName, String email, String password) {
            this.fullName = fullName;
            this.email = email;
            this.password = password;
        }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public static RegisterRequestBuilder builder() { return new RegisterRequestBuilder(); }
        public static class RegisterRequestBuilder {
            private String fullName;
            private String email;
            private String password;
            public RegisterRequestBuilder fullName(String fullName) { this.fullName = fullName; return this; }
            public RegisterRequestBuilder email(String email) { this.email = email; return this; }
            public RegisterRequestBuilder password(String password) { this.password = password; return this; }
            public RegisterRequest build() { return new RegisterRequest(fullName, email, password); }
        }
    }

    public static class OAuth2LoginRequest {
        @NotBlank
        private String provider;

        @NotBlank
        private String token;

        private String email;
        private String fullName;
        private String profilePictureUrl;

        public OAuth2LoginRequest() {}
        public OAuth2LoginRequest(String provider, String token, String email, String fullName, String profilePictureUrl) {
            this.provider = provider;
            this.token = token;
            this.email = email;
            this.fullName = fullName;
            this.profilePictureUrl = profilePictureUrl;
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getProfilePictureUrl() { return profilePictureUrl; }
        public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

        public static OAuth2LoginRequestBuilder builder() { return new OAuth2LoginRequestBuilder(); }
        public static class OAuth2LoginRequestBuilder {
            private String provider;
            private String token;
            private String email;
            private String fullName;
            private String profilePictureUrl;
            public OAuth2LoginRequestBuilder provider(String provider) { this.provider = provider; return this; }
            public OAuth2LoginRequestBuilder token(String token) { this.token = token; return this; }
            public OAuth2LoginRequestBuilder email(String email) { this.email = email; return this; }
            public OAuth2LoginRequestBuilder fullName(String fullName) { this.fullName = fullName; return this; }
            public OAuth2LoginRequestBuilder profilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; return this; }
            public OAuth2LoginRequest build() { return new OAuth2LoginRequest(provider, token, email, fullName, profilePictureUrl); }
        }
    }

    public static class AuthResponse {
        private String accessToken;
        private String tokenType;
        private Long userId;
        private String email;
        private String fullName;
        private AuthProvider provider;

        public AuthResponse() {}
        public AuthResponse(String accessToken, String tokenType, Long userId, String email, String fullName, AuthProvider provider) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.userId = userId;
            this.email = email;
            this.fullName = fullName;
            this.provider = provider;
        }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public AuthProvider getProvider() { return provider; }
        public void setProvider(AuthProvider provider) { this.provider = provider; }

        public static AuthResponseBuilder builder() { return new AuthResponseBuilder(); }
        public static class AuthResponseBuilder {
            private String accessToken;
            private String tokenType;
            private Long userId;
            private String email;
            private String fullName;
            private AuthProvider provider;
            public AuthResponseBuilder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
            public AuthResponseBuilder tokenType(String tokenType) { this.tokenType = tokenType; return this; }
            public AuthResponseBuilder userId(Long userId) { this.userId = userId; return this; }
            public AuthResponseBuilder email(String email) { this.email = email; return this; }
            public AuthResponseBuilder fullName(String fullName) { this.fullName = fullName; return this; }
            public AuthResponseBuilder provider(AuthProvider provider) { this.provider = provider; return this; }
            public AuthResponse build() { return new AuthResponse(accessToken, tokenType, userId, email, fullName, provider); }
        }
    }
}
