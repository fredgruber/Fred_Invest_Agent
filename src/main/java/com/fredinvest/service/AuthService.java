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
    private final OAuthValidationService oauthValidationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       OAuthValidationService oauthValidationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.oauthValidationService = oauthValidationService;
    }

    @SuppressWarnings("null")
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
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user != null && user.getProvider() != AuthProvider.LOCAL && (user.getPasswordHash() == null || user.getPasswordHash().isBlank())) {
            throw new IllegalArgumentException("Esta conta foi cadastrada via " + user.getProvider() + ". Por favor, utilize o botão 'Entrar com Google'.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        if (user == null) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        }

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

        // Valida o token diretamente contra a API oficial do provedor (Google, Microsoft ou Apple)
        OAuthValidationService.SocialUserProfile profile = oauthValidationService.validateAndExtractProfile(provider, request.getToken());

        String email = profile.email();
        @SuppressWarnings("null")
        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    if (profile.fullName() != null && !profile.fullName().isBlank()) {
                        existingUser.setFullName(profile.fullName());
                    }
                    if (profile.pictureUrl() != null && !profile.pictureUrl().isBlank()) {
                        existingUser.setProfilePictureUrl(profile.pictureUrl());
                    }
                    existingUser.setProvider(provider);
                    existingUser.setProviderId(profile.providerId());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .fullName(profile.fullName() != null ? profile.fullName() : "Usuário " + provider)
                            .provider(provider)
                            .providerId(profile.providerId())
                            .profilePictureUrl(profile.pictureUrl())
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
