package com.fredinvest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fredinvest.model.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class OAuthValidationService {

    private static final Logger log = LoggerFactory.getLogger(OAuthValidationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public record SocialUserProfile(String email, String fullName, String providerId, String pictureUrl) {}

    public OAuthValidationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // Construtor para testes unitários com mock/custom client
    public OAuthValidationService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public SocialUserProfile validateAndExtractProfile(AuthProvider provider, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de autenticação não fornecido.");
        }

        String cleanToken = token.trim();

        return switch (provider) {
            case GOOGLE -> validateGoogleToken(cleanToken);
            case MICROSOFT -> validateMicrosoftToken(cleanToken);
            case APPLE -> validateAppleToken(cleanToken);
            default -> throw new IllegalArgumentException("Provedor não suportado para validação direta: " + provider);
        };
    }

    private SocialUserProfile validateGoogleToken(String token) {
        // 1. Tenta validar via endpoint de tokeninfo do Google (para ID Tokens gerados via Google Sign-In)
        try {
            String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + token;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenInfoUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String email = root.path("email").asText(null);
                String name = root.path("name").asText(null);
                String sub = root.path("sub").asText(null);
                String picture = root.path("picture").asText(null);

                if (email != null && !email.isBlank()) {
                    log.info("Token Google validado com sucesso via tokeninfo para o e-mail: {}", email);
                    return new SocialUserProfile(email, name != null ? name : email, sub, picture);
                }
            }
        } catch (Exception e) {
            log.warn("Tentativa de validação via Google tokeninfo falhou: {}", e.getMessage());
        }

        // 2. Se não for ID Token, tenta validar via endpoint de userinfo (para OAuth Access Tokens)
        try {
            String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(userInfoUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String email = root.path("email").asText(null);
                String name = root.path("name").asText(null);
                String sub = root.path("sub").asText(null);
                String picture = root.path("picture").asText(null);

                if (email != null && !email.isBlank()) {
                    log.info("Token Google validado com sucesso via userinfo para o e-mail: {}", email);
                    return new SocialUserProfile(email, name != null ? name : email, sub, picture);
                }
            } else {
                JsonNode errorNode = objectMapper.readTree(resp.body());
                String errorDesc = errorNode.path("error_description").asText(errorNode.path("error").asText(resp.body()));
                throw new IllegalArgumentException("Token Google inválido rejeitado pela API do Google: " + errorDesc);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao conectar na API do Google para validação do token: " + e.getMessage());
        }

        throw new IllegalArgumentException("Token Google não pôde ser verificado pela API do Google. Verifique se o token é válido e não expirou.");
    }

    private SocialUserProfile validateMicrosoftToken(String token) {
        try {
            String url = "https://graph.microsoft.com/v1.0/me";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String email = root.path("mail").asText(null);
                if (email == null || email.isBlank()) {
                    email = root.path("userPrincipalName").asText(null);
                }
                String name = root.path("displayName").asText(null);
                String id = root.path("id").asText(null);

                if (email != null && !email.isBlank()) {
                    log.info("Token Microsoft validado com sucesso via Microsoft Graph para o e-mail: {}", email);
                    return new SocialUserProfile(email, name != null ? name : email, id, null);
                }
            } else {
                JsonNode errorNode = objectMapper.readTree(resp.body());
                String errorMsg = errorNode.path("error").path("message").asText(resp.body());
                throw new IllegalArgumentException("Token Microsoft inválido rejeitado pela API Microsoft Graph: " + errorMsg);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao conectar na API Microsoft Graph para validação do token: " + e.getMessage());
        }

        throw new IllegalArgumentException("Token Microsoft inválido ou não autenticado pela API Microsoft Graph.");
    }

    private SocialUserProfile validateAppleToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Token Apple inválido: formato JWT esperado (header.payload.signature).");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            JsonNode headerNode = objectMapper.readTree(headerJson);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);

            String kid = headerNode.path("kid").asText(null);
            String iss = payloadNode.path("iss").asText(null);
            String sub = payloadNode.path("sub").asText(null);
            String email = payloadNode.path("email").asText(null);
            long exp = payloadNode.path("exp").asLong(0);

            if (!"https://appleid.apple.com".equals(iss)) {
                throw new IllegalArgumentException("Token Apple inválido: issuer incorreto ('" + iss + "'). Esperado: https://appleid.apple.com");
            }

            if (exp > 0 && Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("Token Apple expirado em " + Instant.ofEpochSecond(exp));
            }

            if (sub == null || sub.isBlank()) {
                throw new IllegalArgumentException("Token Apple inválido: subject (sub) ausente.");
            }

            // Tenta validar a assinatura criptográfica contra as chaves públicas da Apple em https://appleid.apple.com/auth/keys
            if (parts.length == 3 && kid != null) {
                boolean signatureValid = verifyAppleSignature(kid, parts[0] + "." + parts[1], parts[2]);
                if (!signatureValid) {
                    throw new IllegalArgumentException("Assinatura do token Apple rejeitada pelas chaves públicas da Apple.");
                }
            }

            if (email == null || email.isBlank()) {
                email = "apple_" + sub + "@fredinvest.com";
            }

            log.info("Token Apple validado com sucesso para sub: {}, email: {}", sub, email);
            return new SocialUserProfile(email, "Usuário Apple (" + (email.contains("@") ? email.split("@")[0] : sub) + ")", sub, null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Falha na validação do token com a Apple: " + e.getMessage());
        }
    }

    private boolean verifyAppleSignature(String kid, String signedData, String signatureB64) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://appleid.apple.com/auth/keys"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode keys = root.path("keys");
                for (JsonNode key : keys) {
                    if (kid.equals(key.path("kid").asText())) {
                        String nStr = key.path("n").asText();
                        String eStr = key.path("e").asText();
                        byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
                        byte[] eBytes = Base64.getUrlDecoder().decode(eStr);
                        BigInteger n = new BigInteger(1, nBytes);
                        BigInteger e = new BigInteger(1, eBytes);
                        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
                        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);

                        Signature sig = Signature.getInstance("SHA256withRSA");
                        sig.initVerify(publicKey);
                        sig.update(signedData.getBytes(StandardCharsets.UTF_8));
                        byte[] sigBytes = Base64.getUrlDecoder().decode(signatureB64);
                        return sig.verify(sigBytes);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Erro ao verificar assinatura com chaves públicas da Apple: {}", ex.getMessage());
        }
        return false;
    }
}

