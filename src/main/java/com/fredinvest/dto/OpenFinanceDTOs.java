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
}

