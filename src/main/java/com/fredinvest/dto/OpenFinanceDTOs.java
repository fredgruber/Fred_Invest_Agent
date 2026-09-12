package com.fredinvest.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public class OpenFinanceDTOs {

    public static class InstitutionDTO {
        private String id;
        private String name;
        private String logoUrl;
        private String primaryColor;

        public InstitutionDTO() {}
        public InstitutionDTO(String id, String name, String logoUrl, String primaryColor) {
            this.id = id;
            this.name = name;
            this.logoUrl = logoUrl;
            this.primaryColor = primaryColor;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getLogoUrl() { return logoUrl; }
        public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

        public String getPrimaryColor() { return primaryColor; }
        public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

        public static InstitutionDTOBuilder builder() { return new InstitutionDTOBuilder(); }
        public static class InstitutionDTOBuilder {
            private String id;
            private String name;
            private String logoUrl;
            private String primaryColor;

            public InstitutionDTOBuilder id(String id) { this.id = id; return this; }
            public InstitutionDTOBuilder name(String name) { this.name = name; return this; }
            public InstitutionDTOBuilder logoUrl(String logoUrl) { this.logoUrl = logoUrl; return this; }
            public InstitutionDTOBuilder primaryColor(String primaryColor) { this.primaryColor = primaryColor; return this; }

            public InstitutionDTO build() { return new InstitutionDTO(id, name, logoUrl, primaryColor); }
        }
    }

    public static class ConsentRequestDTO {
        @NotBlank
        private String institutionId;
        private List<String> permissions;

        public ConsentRequestDTO() {}
        public ConsentRequestDTO(String institutionId, List<String> permissions) {
            this.institutionId = institutionId;
            this.permissions = permissions;
        }

        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }

        public static ConsentRequestDTOBuilder builder() { return new ConsentRequestDTOBuilder(); }
        public static class ConsentRequestDTOBuilder {
            private String institutionId;
            private List<String> permissions;

            public ConsentRequestDTOBuilder institutionId(String institutionId) { this.institutionId = institutionId; return this; }
            public ConsentRequestDTOBuilder permissions(List<String> permissions) { this.permissions = permissions; return this; }

            public ConsentRequestDTO build() { return new ConsentRequestDTO(institutionId, permissions); }
        }
    }

    public static class ConsentResponseDTO {
        private String consentId;
        private String redirectUrl;
        private String status;
        private String institutionName;

        public ConsentResponseDTO() {}
        public ConsentResponseDTO(String consentId, String redirectUrl, String status, String institutionName) {
            this.consentId = consentId;
            this.redirectUrl = redirectUrl;
            this.status = status;
            this.institutionName = institutionName;
        }

        public String getConsentId() { return consentId; }
        public void setConsentId(String consentId) { this.consentId = consentId; }

        public String getRedirectUrl() { return redirectUrl; }
        public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getInstitutionName() { return institutionName; }
        public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

        public static ConsentResponseDTOBuilder builder() { return new ConsentResponseDTOBuilder(); }
        public static class ConsentResponseDTOBuilder {
            private String consentId;
            private String redirectUrl;
            private String status;
            private String institutionName;

            public ConsentResponseDTOBuilder consentId(String consentId) { this.consentId = consentId; return this; }
            public ConsentResponseDTOBuilder redirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; return this; }
            public ConsentResponseDTOBuilder status(String status) { this.status = status; return this; }
            public ConsentResponseDTOBuilder institutionName(String institutionName) { this.institutionName = institutionName; return this; }

            public ConsentResponseDTO build() { return new ConsentResponseDTO(consentId, redirectUrl, status, institutionName); }
        }
    }

    public static class ConsentStatusDTO {
        private Long id;
        private String consentId;
        private String institutionId;
        private String institutionName;
        private String status;
        private LocalDateTime expiresAt;

        public ConsentStatusDTO() {}
        public ConsentStatusDTO(Long id, String consentId, String institutionId, String institutionName, String status, LocalDateTime expiresAt) {
            this.id = id;
            this.consentId = consentId;
            this.institutionId = institutionId;
            this.institutionName = institutionName;
            this.status = status;
            this.expiresAt = expiresAt;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getConsentId() { return consentId; }
        public void setConsentId(String consentId) { this.consentId = consentId; }

        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

        public String getInstitutionName() { return institutionName; }
        public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

        public static ConsentStatusDTOBuilder builder() { return new ConsentStatusDTOBuilder(); }
        public static class ConsentStatusDTOBuilder {
            private Long id;
            private String consentId;
            private String institutionId;
            private String institutionName;
            private String status;
            private LocalDateTime expiresAt;

            public ConsentStatusDTOBuilder id(Long id) { this.id = id; return this; }
            public ConsentStatusDTOBuilder consentId(String consentId) { this.consentId = consentId; return this; }
            public ConsentStatusDTOBuilder institutionId(String institutionId) { this.institutionId = institutionId; return this; }
            public ConsentStatusDTOBuilder institutionName(String institutionName) { this.institutionName = institutionName; return this; }
            public ConsentStatusDTOBuilder status(String status) { this.status = status; return this; }
            public ConsentStatusDTOBuilder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }

            public ConsentStatusDTO build() { return new ConsentStatusDTO(id, consentId, institutionId, institutionName, status, expiresAt); }
        }
    }

    public static class SyncResponseDTO {
        private String status;
        private int importedAssetsCount;
        private String message;
        private LocalDateTime syncedAt;

        public SyncResponseDTO() {}
        public SyncResponseDTO(String status, int importedAssetsCount, String message, LocalDateTime syncedAt) {
            this.status = status;
            this.importedAssetsCount = importedAssetsCount;
            this.message = message;
            this.syncedAt = syncedAt;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getImportedAssetsCount() { return importedAssetsCount; }
        public void setImportedAssetsCount(int importedAssetsCount) { this.importedAssetsCount = importedAssetsCount; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public LocalDateTime getSyncedAt() { return syncedAt; }
        public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

        public static SyncResponseDTOBuilder builder() { return new SyncResponseDTOBuilder(); }
        public static class SyncResponseDTOBuilder {
            private String status;
            private int importedAssetsCount;
            private String message;
            private LocalDateTime syncedAt;

            public SyncResponseDTOBuilder status(String status) { this.status = status; return this; }
            public SyncResponseDTOBuilder importedAssetsCount(int importedAssetsCount) { this.importedAssetsCount = importedAssetsCount; return this; }
            public SyncResponseDTOBuilder message(String message) { this.message = message; return this; }
            public SyncResponseDTOBuilder syncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; return this; }

            public SyncResponseDTO build() { return new SyncResponseDTO(status, importedAssetsCount, message, syncedAt); }
        }
    }
}
