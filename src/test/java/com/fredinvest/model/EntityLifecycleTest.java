package com.fredinvest.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class EntityLifecycleTest {

    @Test
    void userBuilderAndPersistenceCallbacksPopulateTimestamps() {
        User user = User.builder()
                .email("user@example.com")
                .fullName("Test User")
                .provider(AuthProvider.LOCAL)
                .build();

        assertEquals("user@example.com", user.getEmail());
        assertEquals(AuthProvider.LOCAL, user.getProvider());
        assertNull(user.getCreatedAt());

        user.onCreate();
        LocalDateTime createdAt = user.getCreatedAt();
        user.onUpdate();

        assertNotNull(createdAt);
        assertEquals(createdAt, user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    void portfolioBuilderUsesEmptyAssetsWhenNoneProvided() {
        Portfolio portfolio = Portfolio.builder()
                .name("Main")
                .description("Primary portfolio")
                .build();

        assertEquals("Main", portfolio.getName());
        assertNotNull(portfolio.getAssets());
        assertTrue(portfolio.getAssets().isEmpty());

        portfolio.onCreate();
        assertNotNull(portfolio.getCreatedAt());
        assertNotNull(portfolio.getUpdatedAt());
    }

    @Test
    void assetBuilderRetainsValuesAndCallbacksUpdateTimestamps() {
        Portfolio portfolio = Portfolio.builder().name("Main").build();
        Asset asset = Asset.builder()
                .ticker("PETR4")
                .name("Petrobras")
                .category(AssetCategory.ACOES)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("35.50"))
                .portfolio(portfolio)
                .build();

        assertEquals("PETR4", asset.getTicker());
        assertEquals(new BigDecimal("10"), asset.getQuantity());
        assertEquals(AssetCategory.ACOES, asset.getCategory());
        assertSame(portfolio, asset.getPortfolio());

        asset.onCreate();
        assertNotNull(asset.getCreatedAt());
        assertNotNull(asset.getUpdatedAt());
        asset.onUpdate();
        assertNotNull(asset.getUpdatedAt());
    }
}
