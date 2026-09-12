package com.fredinvest.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "open_finance_consents")
public class OpenFinanceConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String institutionId;

    @Column(nullable = false)
    private String institutionName;

    @Column(nullable = false, unique = true)
    private String consentId;

    @Column(nullable = false)
    private String status;

    @Column(length = 2048)
    private String accessToken;

    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public OpenFinanceConsent() {}

    public OpenFinanceConsent(Long id, User user, String institutionId, String institutionName, String consentId, String status, String accessToken, LocalDateTime expiresAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.institutionId = institutionId;
        this.institutionName = institutionName;
        this.consentId = consentId;
        this.status = status;
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

    public String getConsentId() { return consentId; }
    public void setConsentId(String consentId) { this.consentId = consentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static OpenFinanceConsentBuilder builder() {
        return new OpenFinanceConsentBuilder();
    }

    public static class OpenFinanceConsentBuilder {
        private Long id;
        private User user;
        private String institutionId;
        private String institutionName;
        private String consentId;
        private String status;
        private String accessToken;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public OpenFinanceConsentBuilder id(Long id) { this.id = id; return this; }
        public OpenFinanceConsentBuilder user(User user) { this.user = user; return this; }
        public OpenFinanceConsentBuilder institutionId(String institutionId) { this.institutionId = institutionId; return this; }
        public OpenFinanceConsentBuilder institutionName(String institutionName) { this.institutionName = institutionName; return this; }
        public OpenFinanceConsentBuilder consentId(String consentId) { this.consentId = consentId; return this; }
        public OpenFinanceConsentBuilder status(String status) { this.status = status; return this; }
        public OpenFinanceConsentBuilder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
        public OpenFinanceConsentBuilder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
        public OpenFinanceConsentBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public OpenFinanceConsentBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public OpenFinanceConsent build() {
            return new OpenFinanceConsent(id, user, institutionId, institutionName, consentId, status, accessToken, expiresAt, createdAt, updatedAt);
        }
    }
}
