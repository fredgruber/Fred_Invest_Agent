package com.fredinvest.service;

import com.fredinvest.config.JwtTokenProvider;
import com.fredinvest.dto.AuthDTOs.AuthResponse;
import com.fredinvest.dto.AuthDTOs.LoginRequest;
import com.fredinvest.dto.AuthDTOs.OAuth2LoginRequest;
import com.fredinvest.dto.AuthDTOs.RegisterRequest;
import com.fredinvest.model.AuthProvider;
import com.fredinvest.model.User;
import com.fredinvest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesLocalUserAndReturnsToken() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Ana Silva")
                .email("ana@example.com")
                .password("secret")
                .build();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(tokenProvider.generateTokenFromUsername(request.getEmail())).thenReturn("token");

        AuthResponse response = authService.register(request);

        assertEquals("token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(request.getEmail(), response.getEmail());
        assertEquals(AuthProvider.LOCAL, response.getProvider());
        verify(userRepository).save(argThat(user ->
                user.getFullName().equals(request.getFullName())
                        && user.getPasswordHash().equals("encoded")
                        && user.getProvider() == AuthProvider.LOCAL));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Ana Silva")
                .email("ana@example.com")
                .password("secret")
                .build();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertEquals("E-mail já cadastrado no sistema.", exception.getMessage());
        verifyNoInteractions(passwordEncoder, tokenProvider);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginAuthenticatesUserAndReturnsUserDetails() {
        LoginRequest request = LoginRequest.builder()
                .email("ana@example.com")
                .password("secret")
                .build();
        User user = User.builder()
                .id(7L)
                .email(request.getEmail())
                .fullName("Ana Silva")
                .provider(AuthProvider.LOCAL)
                .build();
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(authentication)).thenReturn("login-token");

        AuthResponse response = authService.login(request);

        assertEquals("login-token", response.getAccessToken());
        assertEquals(7L, response.getUserId());
        assertEquals("Ana Silva", response.getFullName());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void socialLoginCreatesUserWhenEmailDoesNotExist() {
        OAuth2LoginRequest request = OAuth2LoginRequest.builder()
                .provider("google")
                .token("oauth-token")
                .email("ana@example.com")
                .fullName("Ana Silva")
                .profilePictureUrl("picture")
                .build();
        User savedUser = User.builder()
                .id(9L)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .provider(AuthProvider.GOOGLE)
                .build();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateTokenFromUsername(request.getEmail())).thenReturn("social-token");

        AuthResponse response = authService.socialLogin(request);

        assertEquals("social-token", response.getAccessToken());
        assertEquals(AuthProvider.GOOGLE, response.getProvider());
        verify(userRepository).save(argThat(user ->
                user.getProvider() == AuthProvider.GOOGLE
                        && user.getProviderId().equals(request.getToken())
                        && user.getProfilePictureUrl().equals("picture")));
    }

    @Test
    void socialLoginRejectsUnknownProvider() {
        OAuth2LoginRequest request = OAuth2LoginRequest.builder()
                .provider("unknown")
                .token("oauth-token")
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.socialLogin(request));

        assertTrue(exception.getMessage().contains("Provedor OAuth2 inválido"));
        verifyNoInteractions(userRepository, tokenProvider);
    }
}
