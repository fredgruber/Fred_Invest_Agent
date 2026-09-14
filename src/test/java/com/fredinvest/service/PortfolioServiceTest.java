package com.fredinvest.service;

import com.fredinvest.dto.AssetTransactionDTO;
import com.fredinvest.dto.PortfolioDTOs.AssetDTO;
import com.fredinvest.dto.PortfolioDTOs.PortfolioRequest;
import com.fredinvest.dto.PortfolioDTOs.PortfolioResponse;
import com.fredinvest.dto.PortfolioDTOs.PortfolioSummaryDTO;
import com.fredinvest.model.Asset;
import com.fredinvest.model.AssetCategory;
import com.fredinvest.model.AssetTransaction;
import com.fredinvest.model.Portfolio;
import com.fredinvest.model.User;
import com.fredinvest.repository.AssetRepository;
import com.fredinvest.repository.PortfolioRepository;
import com.fredinvest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.fredinvest.repository.AssetTransactionRepository assetTransactionRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    @SuppressWarnings("null")
	@Test
    void createPortfolioMapsSavedPortfolio() {
        User user = user();
        Portfolio savedPortfolio = Portfolio.builder()
                .id(3L)
                .name("Long term")
                .description("Retirement")
                .user(user)
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(savedPortfolio);

        PortfolioResponse response = portfolioService.createPortfolio(
                PortfolioRequest.builder().name("Long term").description("Retirement").build(),
                user.getEmail());

        assertEquals(3L, response.getId());
        assertEquals("Long term", response.getName());
        assertEquals(BigDecimal.ZERO, response.getTotalValue());
        verify(portfolioRepository).save(any(Portfolio.class));
    }

    @SuppressWarnings("null")
	@Test
    void getUserPortfoliosCreatesDefaultWhenUserHasNoPortfolios() {
        User user = user();
        Portfolio defaultPortfolio = Portfolio.builder()
                .id(4L)
                .name("Carteira Principal")
                .user(user)
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserId(user.getId())).thenReturn(List.of());
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(defaultPortfolio);

        List<PortfolioResponse> responses = portfolioService.getUserPortfolios(user.getEmail());

        assertEquals(1, responses.size());
        assertEquals("Carteira Principal", responses.get(0).getName());
        verify(portfolioRepository).save(argThat(portfolio ->
                "Carteira Principal".equals(portfolio.getName())
                        && portfolio.getUser().equals(user)));
    }

    @SuppressWarnings("null")
	@Test
    void addOrUpdateAssetCreatesUppercaseAssetAndUsesAveragePriceFallback() {
        User user = user();
        Portfolio portfolio = portfolio(user);
        AssetDTO request = AssetDTO.builder()
                .ticker("test4")
                .name("Test Asset")
                .category(AssetCategory.ACOES)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("35.50"))
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findById(portfolio.getId())).thenReturn(Optional.of(portfolio));
        when(assetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "TEST4"))
                .thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetDTO response = portfolioService.addOrUpdateAsset(portfolio.getId(), request, user.getEmail());

        assertEquals("TEST4", response.getTicker());
        assertEquals(new BigDecimal("355.00"), response.getTotalValue());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getGainLoss());
        verify(assetRepository).save(argThat(asset ->
                asset.getTicker().equals("TEST4")
                        && asset.getCurrentPrice().equals(request.getAveragePrice())));
    }

    @SuppressWarnings("null")
	@Test
    void addOrUpdateAssetRejectsPortfolioOwnedByAnotherUser() {
        User authenticatedUser = user();
        User owner = User.builder().id(99L).email("owner@example.com").fullName("Owner").build();
        Portfolio portfolio = portfolio(owner);
        when(userRepository.findByEmail(authenticatedUser.getEmail())).thenReturn(Optional.of(authenticatedUser));
        when(portfolioRepository.findById(portfolio.getId())).thenReturn(Optional.of(portfolio));

        SecurityException exception = assertThrows(SecurityException.class, () ->
                portfolioService.addOrUpdateAsset(portfolio.getId(), AssetDTO.builder().build(), authenticatedUser.getEmail()));

        assertEquals("Acesso negado a esta carteira.", exception.getMessage());
        verifyNoInteractions(assetRepository);
    }

    @Test
    void getPortfolioSummaryCalculatesGainAndCategoryAllocation() {
        User user = user();
        AssetDTO asset = AssetDTO.builder()
                .ticker("TEST4")
                .category(AssetCategory.ACOES)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("10"))
                .currentPrice(new BigDecimal("12"))
                .totalValue(new BigDecimal("120"))
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserId(user.getId())).thenReturn(List.of(
                Portfolio.builder().id(1L).name("Main").user(user).build()));
        Portfolio portfolio = Portfolio.builder().id(1L).name("Main").user(user).build();
        portfolio.setAssets(List.of(Asset.builder()
                .id(1L).ticker("TEST4").name("Test Asset").category(AssetCategory.ACOES)
                .quantity(asset.getQuantity()).averagePrice(asset.getAveragePrice())
                .currentPrice(asset.getCurrentPrice()).portfolio(portfolio).build()));
        when(portfolioRepository.findByUserId(user.getId())).thenReturn(List.of(portfolio));

        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(user.getEmail());

        assertEquals(new BigDecimal("120"), summary.getTotalPatrimony());
        assertEquals(new BigDecimal("100"), summary.getTotalInvested());
        assertEquals(new BigDecimal("20"), summary.getTotalGainLoss());
        assertEquals(new BigDecimal("20.0000"), summary.getGainLossPercentage());
        assertEquals(1, summary.getAllocations().size());
        assertEquals(new BigDecimal("100.0000"), summary.getAllocations().get(0).getPercentage());
    }

    @Test
    void determineUnderlyingTickerResolvesCorrectStockFromDescriptionAndTicker() {
        assertEquals("VALE3", PortfolioService.determineUnderlyingTicker("VALEJ854", "VALEE ON 83,64"));
        assertEquals("PETR4", PortfolioService.determineUnderlyingTicker("PETRJ380", "PETR PN 38.00"));
        assertEquals("BOVA11", PortfolioService.determineUnderlyingTicker("BOVAW120", null));
        assertEquals("VALE3", PortfolioService.determineUnderlyingTicker("VALEA800", null));
        assertEquals("ITUB4", PortfolioService.determineUnderlyingTicker("ITUBJ350", null));
    }

    @Test
    void deleteAssetTransactionRecalculatesAssetQuantityAndAveragePrice() {
        User user = user();
        Portfolio portfolio = portfolio(user);
        Asset asset = Asset.builder()
                .id(1L)
                .ticker("PETR4")
                .portfolio(portfolio)
                .quantity(new BigDecimal("150"))
                .averagePrice(new BigDecimal("20"))
                .build();
        AssetTransaction tx1 = AssetTransaction.builder()
                .id(101L)
                .asset(asset)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("10"))
                .totalValue(new BigDecimal("1000"))
                .build();
        AssetTransaction tx2 = AssetTransaction.builder()
                .id(102L)
                .asset(asset)
                .quantity(new BigDecimal("50"))
                .price(new BigDecimal("20"))
                .totalValue(new BigDecimal("1000"))
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findById(portfolio.getId())).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(assetTransactionRepository.findById(tx2.getId())).thenReturn(Optional.of(tx2));
        when(assetTransactionRepository.findByAssetIdOrderByTransactionDateDesc(asset.getId()))
                .thenReturn(List.of(tx1));

        boolean assetDeleted = portfolioService.deleteAssetTransaction(portfolio.getId(), asset.getId(), tx2.getId(), user.getEmail());

        assertFalse(assetDeleted);
        assertEquals(new BigDecimal("100"), asset.getQuantity());
        assertEquals(new BigDecimal("10.0000"), asset.getAveragePrice());
        verify(assetTransactionRepository).delete(tx2);
        verify(assetRepository).save(asset);
    }

    @Test
    void updateAssetTransactionQuantityRecalculatesAssetTotals() {
        User user = user();
        Portfolio portfolio = portfolio(user);
        Asset asset = Asset.builder()
                .id(1L)
                .ticker("PETR4")
                .portfolio(portfolio)
                .quantity(new BigDecimal("100"))
                .averagePrice(new BigDecimal("10"))
                .build();
        AssetTransaction tx = AssetTransaction.builder()
                .id(101L)
                .asset(asset)
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("10"))
                .totalValue(new BigDecimal("1000"))
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findById(portfolio.getId())).thenReturn(Optional.of(portfolio));
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(assetTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(assetTransactionRepository.findByAssetIdOrderByTransactionDateDesc(asset.getId()))
                .thenReturn(List.of(tx));

        List<AssetTransactionDTO> result = portfolioService.updateAssetTransactionQuantity(
                portfolio.getId(), asset.getId(), tx.getId(), new BigDecimal("200"), user.getEmail());

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("200"), tx.getQuantity());
        assertEquals(new BigDecimal("2000.00"), tx.getTotalValue().setScale(2));
        assertEquals(new BigDecimal("200"), asset.getQuantity());
        verify(assetRepository).save(asset);
    }

    private User user() {
        return User.builder().id(10L).email("user@example.com").fullName("User").build();
    }

    private Portfolio portfolio(User user) {
        return Portfolio.builder().id(20L).name("Main").user(user).build();
    }
}
