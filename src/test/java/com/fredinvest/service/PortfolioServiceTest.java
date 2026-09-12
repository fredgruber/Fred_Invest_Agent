package com.fredinvest.service;

import com.fredinvest.dto.PortfolioDTOs.AssetDTO;
import com.fredinvest.dto.PortfolioDTOs.PortfolioRequest;
import com.fredinvest.dto.PortfolioDTOs.PortfolioResponse;
import com.fredinvest.dto.PortfolioDTOs.PortfolioSummaryDTO;
import com.fredinvest.model.Asset;
import com.fredinvest.model.AssetCategory;
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
                .ticker("petr4")
                .name("Petrobras")
                .category(AssetCategory.ACOES)
                .quantity(new BigDecimal("10"))
                .averagePrice(new BigDecimal("35.50"))
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(portfolioRepository.findById(portfolio.getId())).thenReturn(Optional.of(portfolio));
        when(assetRepository.findByPortfolioIdAndTicker(portfolio.getId(), "PETR4"))
                .thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetDTO response = portfolioService.addOrUpdateAsset(portfolio.getId(), request, user.getEmail());

        assertEquals("PETR4", response.getTicker());
        assertEquals(new BigDecimal("355.00"), response.getTotalValue());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getGainLoss());
        verify(assetRepository).save(argThat(asset ->
                asset.getTicker().equals("PETR4")
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
                .ticker("PETR4")
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
                .id(1L).ticker("PETR4").name("Petrobras").category(AssetCategory.ACOES)
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

    private User user() {
        return User.builder().id(10L).email("user@example.com").fullName("User").build();
    }

    private Portfolio portfolio(User user) {
        return Portfolio.builder().id(20L).name("Main").user(user).build();
    }
}
