package com.fredinvest.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetCategory category;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal strikePrice;

    private java.time.LocalDate expirationDate;

    private String underlyingTicker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Asset() {}

    public Asset(Long id, String ticker, String name, AssetCategory category, BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice, Portfolio portfolio, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, ticker, name, category, quantity, averagePrice, currentPrice, portfolio, createdAt, updatedAt, null, null, null);
    }

    public Asset(Long id, String ticker, String name, AssetCategory category, BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice, Portfolio portfolio, LocalDateTime createdAt, LocalDateTime updatedAt, BigDecimal strikePrice, java.time.LocalDate expirationDate) {
        this(id, ticker, name, category, quantity, averagePrice, currentPrice, portfolio, createdAt, updatedAt, strikePrice, expirationDate, null);
    }

    public Asset(Long id, String ticker, String name, AssetCategory category, BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice, Portfolio portfolio, LocalDateTime createdAt, LocalDateTime updatedAt, BigDecimal strikePrice, java.time.LocalDate expirationDate, String underlyingTicker) {
        this.id = id;
        this.ticker = ticker;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.currentPrice = currentPrice;
        this.portfolio = portfolio;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.strikePrice = strikePrice;
        this.expirationDate = expirationDate;
        this.underlyingTicker = underlyingTicker;
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

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AssetCategory getCategory() { return category; }
    public void setCategory(AssetCategory category) { this.category = category; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getStrikePrice() { return strikePrice; }
    public void setStrikePrice(BigDecimal strikePrice) { this.strikePrice = strikePrice; }

    public java.time.LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(java.time.LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public String getUnderlyingTicker() { return underlyingTicker; }
    public void setUnderlyingTicker(String underlyingTicker) { this.underlyingTicker = underlyingTicker; }

    public static AssetBuilder builder() {
        return new AssetBuilder();
    }

    public static class AssetBuilder {
        private Long id;
        private String ticker;
        private String name;
        private AssetCategory category;
        private BigDecimal quantity;
        private BigDecimal averagePrice;
        private BigDecimal currentPrice;
        private Portfolio portfolio;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private BigDecimal strikePrice;
        private java.time.LocalDate expirationDate;
        private String underlyingTicker;

        public AssetBuilder id(Long id) { this.id = id; return this; }
        public AssetBuilder ticker(String ticker) { this.ticker = ticker; return this; }
        public AssetBuilder name(String name) { this.name = name; return this; }
        public AssetBuilder category(AssetCategory category) { this.category = category; return this; }
        public AssetBuilder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public AssetBuilder averagePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; return this; }
        public AssetBuilder currentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; return this; }
        public AssetBuilder portfolio(Portfolio portfolio) { this.portfolio = portfolio; return this; }
        public AssetBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public AssetBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public AssetBuilder strikePrice(BigDecimal strikePrice) { this.strikePrice = strikePrice; return this; }
        public AssetBuilder expirationDate(java.time.LocalDate expirationDate) { this.expirationDate = expirationDate; return this; }
        public AssetBuilder underlyingTicker(String underlyingTicker) { this.underlyingTicker = underlyingTicker; return this; }

        public Asset build() {
            return new Asset(id, ticker, name, category, quantity, averagePrice, currentPrice, portfolio, createdAt, updatedAt, strikePrice, expirationDate, underlyingTicker);
        }
    }
}
