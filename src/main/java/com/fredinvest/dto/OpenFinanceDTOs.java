package com.fredinvest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class OpenFinanceDTOs {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstitutionDTO {
        private String id;
        private String name;
        private String logoUrl;
        private String primaryColor;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsentRequestDTO {
        @NotBlank
        private String institutionId;
        private List<String> permissions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsentResponseDTO {
        private String consentId;
        private String redirectUrl;
        private String status;
        private String institutionName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsentStatusDTO {
        private Long id;
        private String consentId;
        private String institutionId;
        private String institutionName;
        private String status;
        private LocalDateTime expiresAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncResponseDTO {
        private String status;
        private int importedAssetsCount;
        private String message;
        private LocalDateTime syncedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class XpAuthUrlResponseDTO {
        private String authUrl;
        private String consentId;
        private String state;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class XpCallbackRequestDTO {
        private String code;
        private String state;
        private String consentId;
        @Builder.Default
        private boolean replacePortfolio = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class XpConfigDTO {
        private String clientId;
        private String clientSecret;
        private boolean hasClientSecret;
        private String authUrl;
        private String tokenUrl;
        private String apiUrl;
        private String redirectUri;
        private String pluggyClientId;
        private String pluggyClientSecret;
        private boolean hasPluggySecret;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class XpConnectRequestDTO {
        private String accountNumber;
        private String clientCpf;
        private String apiToken;
        private String clientId;
        private String clientSecret;
        private String pluggyApiKey;
        private List<String> scopes;
        private boolean autoSync;
        @Builder.Default
        private boolean replacePortfolio = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class XpStatusDTO {
        private boolean connected;
        private Long consentId;
        private String accountNumber;
        private String status;
        private String provider;
        private LocalDateTime expiresAt;
        private LocalDateTime lastSyncAt;
        private int importedAssetsCount;
    }
}
