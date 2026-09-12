package com.fredinvest.service;

import com.fredinvest.config.JwtTokenProvider;
import com.fredinvest.dto.AuthDTOs.*;
import com.fredinvest.model.AuthProvider;
import com.fredinvest.model.User;
import com.fredinvest.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("E-mail já cadastrado no sistema.");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .provider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);

        String token = tokenProvider.generateTokenFromUsername(user.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .provider(user.getProvider())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        String token = tokenProvider.generateToken(authentication);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .provider(user.getProvider())
                .build();
    }

    @Transactional
    public AuthResponse socialLogin(OAuth2LoginRequest request) {
        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(request.getProvider().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Provedor OAuth2 inválido: " + request.getProvider());
        }

        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = request.getProvider().toLowerCase() + "_user_" + System.currentTimeMillis() + "@fredinvest.com";
        }

        String finalEmail = email;
        User user = userRepository.findByEmail(finalEmail)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(finalEmail)
                            .fullName(request.getFullName() != null ? request.getFullName() : "Usuário " + provider)
                            .provider(provider)
                            .providerId(request.getToken())
                            .profilePictureUrl(request.getProfilePictureUrl())
                            .build();
                    return userRepository.save(newUser);
                });

        String token = tokenProvider.generateTokenFromUsername(user.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .provider(user.getProvider())
                .build();
    }
}
