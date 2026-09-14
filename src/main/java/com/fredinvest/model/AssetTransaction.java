package com.fredinvest.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_transactions")
public class AssetTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AssetTransaction() {}

    public AssetTransaction(Long id, Asset asset, BigDecimal quantity, BigDecimal price, BigDecimal totalValue, LocalDateTime transactionDate, LocalDateTime createdAt) {
        this.id = id;
        this.asset = asset;
        this.quantity = quantity;
        this.price = price;
        this.totalValue = totalValue;
        this.transactionDate = transactionDate;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.transactionDate == null) {
            this.transactionDate = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static AssetTransactionBuilder builder() {
        return new AssetTransactionBuilder();
    }

    public static class AssetTransactionBuilder {
        private Long id;
        private Asset asset;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal totalValue;
        private LocalDateTime transactionDate;
        private LocalDateTime createdAt;

        public AssetTransactionBuilder id(Long id) { this.id = id; return this; }
        public AssetTransactionBuilder asset(Asset asset) { this.asset = asset; return this; }
        public AssetTransactionBuilder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public AssetTransactionBuilder price(BigDecimal price) { this.price = price; return this; }
        public AssetTransactionBuilder totalValue(BigDecimal totalValue) { this.totalValue = totalValue; return this; }
        public AssetTransactionBuilder transactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; return this; }
        public AssetTransactionBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public AssetTransaction build() {
            return new AssetTransaction(id, asset, quantity, price, totalValue, transactionDate, createdAt);
        }
    }
}

