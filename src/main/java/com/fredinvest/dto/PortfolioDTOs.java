package com.fredinvest.dto;

import com.fredinvest.model.AssetCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioDTOs {

    public static class AssetDTO {
        private Long id;

        @NotBlank
        private String ticker;

        @NotBlank
        private String name;

        @NotNull
        private AssetCategory category;

        @NotNull
        @Positive
        private BigDecimal quantity;

        @NotNull
        @Positive
        private BigDecimal averagePrice;

        private BigDecimal currentPrice;
        private BigDecimal totalValue;
        private BigDecimal gainLoss;
        private BigDecimal gainLossPercentage;

        public AssetDTO() {}
        public AssetDTO(Long id, String ticker, String name, AssetCategory category, BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice, BigDecimal totalValue, BigDecimal gainLoss, BigDecimal gainLossPercentage) {
            this.id = id;
            this.ticker = ticker;
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.averagePrice = averagePrice;
            this.currentPrice = currentPrice;
            this.totalValue = totalValue;
            this.gainLoss = gainLoss;
            this.gainLossPercentage = gainLossPercentage;
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

        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

        public BigDecimal getGainLoss() { return gainLoss; }
        public void setGainLoss(BigDecimal gainLoss) { this.gainLoss = gainLoss; }

        public BigDecimal getGainLossPercentage() { return gainLossPercentage; }
        public void setGainLossPercentage(BigDecimal gainLossPercentage) { this.gainLossPercentage = gainLossPercentage; }

        public static AssetDTOBuilder builder() { return new AssetDTOBuilder(); }
        public static class AssetDTOBuilder {
            private Long id;
            private String ticker;
            private String name;
            private AssetCategory category;
            private BigDecimal quantity;
            private BigDecimal averagePrice;
            private BigDecimal currentPrice;
            private BigDecimal totalValue;
            private BigDecimal gainLoss;
            private BigDecimal gainLossPercentage;

            public AssetDTOBuilder id(Long id) { this.id = id; return this; }
            public AssetDTOBuilder ticker(String ticker) { this.ticker = ticker; return this; }
            public AssetDTOBuilder name(String name) { this.name = name; return this; }
            public AssetDTOBuilder category(AssetCategory category) { this.category = category; return this; }
            public AssetDTOBuilder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
            public AssetDTOBuilder averagePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; return this; }
            public AssetDTOBuilder currentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; return this; }
            public AssetDTOBuilder totalValue(BigDecimal totalValue) { this.totalValue = totalValue; return this; }
            public AssetDTOBuilder gainLoss(BigDecimal gainLoss) { this.gainLoss = gainLoss; return this; }
            public AssetDTOBuilder gainLossPercentage(BigDecimal gainLossPercentage) { this.gainLossPercentage = gainLossPercentage; return this; }

            public AssetDTO build() {
                return new AssetDTO(id, ticker, name, category, quantity, averagePrice, currentPrice, totalValue, gainLoss, gainLossPercentage);
            }
        }
    }

    public static class PortfolioRequest {
        @NotBlank
        private String name;
        private String description;

        public PortfolioRequest() {}
        public PortfolioRequest(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public static PortfolioRequestBuilder builder() { return new PortfolioRequestBuilder(); }
        public static class PortfolioRequestBuilder {
            private String name;
            private String description;
            public PortfolioRequestBuilder name(String name) { this.name = name; return this; }
            public PortfolioRequestBuilder description(String description) { this.description = description; return this; }
            public PortfolioRequest build() { return new PortfolioRequest(name, description); }
        }
    }

    public static class PortfolioResponse {
        private Long id;
        private String name;
        private String description;
        private BigDecimal totalValue;
        private List<AssetDTO> assets;

        public PortfolioResponse() {}
        public PortfolioResponse(Long id, String name, String description, BigDecimal totalValue, List<AssetDTO> assets) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.totalValue = totalValue;
            this.assets = assets;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

        public List<AssetDTO> getAssets() { return assets; }
        public void setAssets(List<AssetDTO> assets) { this.assets = assets; }

        public static PortfolioResponseBuilder builder() { return new PortfolioResponseBuilder(); }
        public static class PortfolioResponseBuilder {
            private Long id;
            private String name;
            private String description;
            private BigDecimal totalValue;
            private List<AssetDTO> assets;

            public PortfolioResponseBuilder id(Long id) { this.id = id; return this; }
            public PortfolioResponseBuilder name(String name) { this.name = name; return this; }
            public PortfolioResponseBuilder description(String description) { this.description = description; return this; }
            public PortfolioResponseBuilder totalValue(BigDecimal totalValue) { this.totalValue = totalValue; return this; }
            public PortfolioResponseBuilder assets(List<AssetDTO> assets) { this.assets = assets; return this; }

            public PortfolioResponse build() { return new PortfolioResponse(id, name, description, totalValue, assets); }
        }
    }

    public static class CategoryAllocationDTO {
        private AssetCategory category;
        private BigDecimal totalValue;
        private BigDecimal percentage;

        public CategoryAllocationDTO() {}
        public CategoryAllocationDTO(AssetCategory category, BigDecimal totalValue, BigDecimal percentage) {
            this.category = category;
            this.totalValue = totalValue;
            this.percentage = percentage;
        }

        public AssetCategory getCategory() { return category; }
        public void setCategory(AssetCategory category) { this.category = category; }

        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

        public BigDecimal getPercentage() { return percentage; }
        public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

        public static CategoryAllocationDTOBuilder builder() { return new CategoryAllocationDTOBuilder(); }
        public static class CategoryAllocationDTOBuilder {
            private AssetCategory category;
            private BigDecimal totalValue;
            private BigDecimal percentage;

            public CategoryAllocationDTOBuilder category(AssetCategory category) { this.category = category; return this; }
            public CategoryAllocationDTOBuilder totalValue(BigDecimal totalValue) { this.totalValue = totalValue; return this; }
            public CategoryAllocationDTOBuilder percentage(BigDecimal percentage) { this.percentage = percentage; return this; }

            public CategoryAllocationDTO build() { return new CategoryAllocationDTO(category, totalValue, percentage); }
        }
    }

    public static class PortfolioSummaryDTO {
        private BigDecimal totalPatrimony;
        private BigDecimal totalInvested;
        private BigDecimal totalGainLoss;
        private BigDecimal gainLossPercentage;
        private List<CategoryAllocationDTO> allocations;
        private List<PortfolioResponse> portfolios;

        public PortfolioSummaryDTO() {}
        public PortfolioSummaryDTO(BigDecimal totalPatrimony, BigDecimal totalInvested, BigDecimal totalGainLoss, BigDecimal gainLossPercentage, List<CategoryAllocationDTO> allocations, List<PortfolioResponse> portfolios) {
            this.totalPatrimony = totalPatrimony;
            this.totalInvested = totalInvested;
            this.totalGainLoss = totalGainLoss;
            this.gainLossPercentage = gainLossPercentage;
            this.allocations = allocations;
            this.portfolios = portfolios;
        }

        public BigDecimal getTotalPatrimony() { return totalPatrimony; }
        public void setTotalPatrimony(BigDecimal totalPatrimony) { this.totalPatrimony = totalPatrimony; }

        public BigDecimal getTotalInvested() { return totalInvested; }
        public void setTotalInvested(BigDecimal totalInvested) { this.totalInvested = totalInvested; }

        public BigDecimal getTotalGainLoss() { return totalGainLoss; }
        public void setTotalGainLoss(BigDecimal totalGainLoss) { this.totalGainLoss = totalGainLoss; }

        public BigDecimal getGainLossPercentage() { return gainLossPercentage; }
        public void setGainLossPercentage(BigDecimal gainLossPercentage) { this.gainLossPercentage = gainLossPercentage; }

        public List<CategoryAllocationDTO> getAllocations() { return allocations; }
        public void setAllocations(List<CategoryAllocationDTO> allocations) { this.allocations = allocations; }

        public List<PortfolioResponse> getPortfolios() { return portfolios; }
        public void setPortfolios(List<PortfolioResponse> portfolios) { this.portfolios = portfolios; }

        public static PortfolioSummaryDTOBuilder builder() { return new PortfolioSummaryDTOBuilder(); }
        public static class PortfolioSummaryDTOBuilder {
            private BigDecimal totalPatrimony;
            private BigDecimal totalInvested;
            private BigDecimal totalGainLoss;
            private BigDecimal gainLossPercentage;
            private List<CategoryAllocationDTO> allocations;
            private List<PortfolioResponse> portfolios;

            public PortfolioSummaryDTOBuilder totalPatrimony(BigDecimal totalPatrimony) { this.totalPatrimony = totalPatrimony; return this; }
            public PortfolioSummaryDTOBuilder totalInvested(BigDecimal totalInvested) { this.totalInvested = totalInvested; return this; }
            public PortfolioSummaryDTOBuilder totalGainLoss(BigDecimal totalGainLoss) { this.totalGainLoss = totalGainLoss; return this; }
            public PortfolioSummaryDTOBuilder gainLossPercentage(BigDecimal gainLossPercentage) { this.gainLossPercentage = gainLossPercentage; return this; }
            public PortfolioSummaryDTOBuilder allocations(List<CategoryAllocationDTO> allocations) { this.allocations = allocations; return this; }
            public PortfolioSummaryDTOBuilder portfolios(List<PortfolioResponse> portfolios) { this.portfolios = portfolios; return this; }

            public PortfolioSummaryDTO build() { return new PortfolioSummaryDTO(totalPatrimony, totalInvested, totalGainLoss, gainLossPercentage, allocations, portfolios); }
        }
    }
}
