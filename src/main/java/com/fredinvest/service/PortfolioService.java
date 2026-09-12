package com.fredinvest.service;

import com.fredinvest.dto.PortfolioDTOs.*;
import com.fredinvest.model.Asset;
import com.fredinvest.model.AssetCategory;
import com.fredinvest.model.Portfolio;
import com.fredinvest.model.User;
import com.fredinvest.repository.AssetRepository;
import com.fredinvest.repository.PortfolioRepository;
import com.fredinvest.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, AssetRepository assetRepository, UserRepository userRepository) {
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
    }

    @Transactional
    public PortfolioResponse createPortfolio(PortfolioRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        Portfolio portfolio = Portfolio.builder()
                .name(request.getName())
                .description(request.getDescription())
                .user(user)
                .build();

        portfolio = portfolioRepository.save(portfolio);
        return mapToPortfolioResponse(portfolio);
    }

    public List<PortfolioResponse> getUserPortfolios(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());

        if (portfolios.isEmpty()) {
            // Criar carteira padrão se o usuário não possuir nenhuma
            Portfolio defaultPortfolio = Portfolio.builder()
                    .name("Carteira Principal")
                    .description("Sua carteira de investimentos padrão")
                    .user(user)
                    .build();
            defaultPortfolio = portfolioRepository.save(defaultPortfolio);
            portfolios = List.of(defaultPortfolio);
        }

        return portfolios.stream()
                .map(this::mapToPortfolioResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AssetDTO addOrUpdateAsset(Long portfolioId, AssetDTO assetDTO, String userEmail) {
        User user = getUserByEmail(userEmail);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Carteira não encontrada."));

        if (!portfolio.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Acesso negado a esta carteira.");
        }

        Optional<Asset> existingAsset = assetRepository.findByPortfolioIdAndTicker(portfolioId, assetDTO.getTicker().toUpperCase());

        BigDecimal currentPrice = fetchLivePrice(assetDTO.getTicker(), assetDTO.getCategory());
        if (currentPrice == null) {
            currentPrice = assetDTO.getCurrentPrice() != null ? assetDTO.getCurrentPrice() : assetDTO.getAveragePrice();
        }

        Asset asset;
        if (existingAsset.isPresent()) {
            asset = existingAsset.get();
            asset.setName(assetDTO.getName());
            asset.setCategory(assetDTO.getCategory());
            
            // Calculate new average price and quantity
            BigDecimal oldTotal = asset.getQuantity().multiply(asset.getAveragePrice());
            BigDecimal addedTotal = assetDTO.getQuantity().multiply(assetDTO.getAveragePrice());
            BigDecimal newQuantity = asset.getQuantity().add(assetDTO.getQuantity());
            BigDecimal newAvg = oldTotal.add(addedTotal).divide(newQuantity, 4, RoundingMode.HALF_UP);

            asset.setQuantity(newQuantity);
            asset.setAveragePrice(newAvg);
            asset.setCurrentPrice(currentPrice);
        } else {
            asset = Asset.builder()
                    .ticker(assetDTO.getTicker().toUpperCase())
                    .name(assetDTO.getName())
                    .category(assetDTO.getCategory())
                    .quantity(assetDTO.getQuantity())
                    .averagePrice(assetDTO.getAveragePrice())
                    .currentPrice(currentPrice)
                    .portfolio(portfolio)
                    .build();
        }

        asset = assetRepository.save(asset);
        return mapToAssetDTO(asset);
    }

    private BigDecimal fetchLivePrice(String ticker, AssetCategory category) {
        try {
            String queryTicker = ticker.toUpperCase();
            if ((category == AssetCategory.ACOES || category == AssetCategory.FIIS || category == AssetCategory.OPCOES) && !queryTicker.endsWith(".SA")) {
                queryTicker += ".SA";
            }
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + queryTicker;
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp.body());
                com.fasterxml.jackson.databind.JsonNode result = root.path("chart").path("result");
                if (result.isArray() && result.size() > 0) {
                    com.fasterxml.jackson.databind.JsonNode meta = result.get(0).path("meta");
                    if (meta.has("regularMarketPrice")) {
                        return new BigDecimal(meta.path("regularMarketPrice").asText());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar preco no Yahoo Finance para " + ticker + ": " + e.getMessage());
        }
        return null;
    }

    @Transactional
    public AssetDTO updateAssetPrice(Long portfolioId, Long assetId, BigDecimal newPrice, String userEmail) {
        User user = getUserByEmail(userEmail);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Carteira não encontrada."));

        if (!portfolio.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Acesso negado a esta carteira.");
        }

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Ativo não encontrado."));

        if (!asset.getPortfolio().getId().equals(portfolio.getId())) {
            throw new IllegalArgumentException("Ativo não pertence a esta carteira.");
        }

        asset.setCurrentPrice(newPrice);
        asset = assetRepository.save(asset);
        
        // Return without live fetching to preserve manual edit
        return mapToAssetDTOWithFixedPrice(asset, newPrice);
    }

    @Transactional
    public void deleteAsset(Long portfolioId, Long assetId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Carteira não encontrada."));

        if (!portfolio.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Acesso negado a esta carteira.");
        }

        assetRepository.deleteById(assetId);
    }

    public PortfolioSummaryDTO getPortfolioSummary(String userEmail) {
        List<PortfolioResponse> portfolios = getUserPortfolios(userEmail);

        BigDecimal totalPatrimony = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;

        Map<AssetCategory, BigDecimal> categoryTotals = new HashMap<>();

        for (PortfolioResponse p : portfolios) {
            for (AssetDTO a : p.getAssets()) {
                BigDecimal totalValue = a.getTotalValue();
                BigDecimal investedValue = a.getQuantity().multiply(a.getAveragePrice());

                totalPatrimony = totalPatrimony.add(totalValue);
                totalInvested = totalInvested.add(investedValue);

                categoryTotals.merge(a.getCategory(), totalValue, BigDecimal::add);
            }
        }

        BigDecimal totalGainLoss = totalPatrimony.subtract(totalInvested);
        BigDecimal gainLossPct = BigDecimal.ZERO;

        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            gainLossPct = totalGainLoss.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        BigDecimal finalTotalPatrimony = totalPatrimony;
        List<CategoryAllocationDTO> allocations = categoryTotals.entrySet().stream()
                .map(entry -> {
                    BigDecimal pct = BigDecimal.ZERO;
                    if (finalTotalPatrimony.compareTo(BigDecimal.ZERO) > 0) {
                        pct = entry.getValue().divide(finalTotalPatrimony, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                    }
                    return CategoryAllocationDTO.builder()
                            .category(entry.getKey())
                            .totalValue(entry.getValue())
                            .percentage(pct)
                            .build();
                })
                .collect(Collectors.toList());

        return PortfolioSummaryDTO.builder()
                .totalPatrimony(totalPatrimony)
                .totalInvested(totalInvested)
                .totalGainLoss(totalGainLoss)
                .gainLossPercentage(gainLossPct)
                .allocations(allocations)
                .portfolios(portfolios)
                .build();
    }

    private PortfolioResponse mapToPortfolioResponse(Portfolio portfolio) {
        List<AssetDTO> assetDTOs = portfolio.getAssets().stream()
                .map(this::mapToAssetDTO)
                .collect(Collectors.toList());

        BigDecimal totalValue = assetDTOs.stream()
                .map(AssetDTO::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .description(portfolio.getDescription())
                .totalValue(totalValue)
                .assets(assetDTOs)
                .build();
    }

    private AssetDTO mapToAssetDTO(Asset asset) {
        BigDecimal livePrice = fetchLivePrice(asset.getTicker(), asset.getCategory());
        BigDecimal currentPrice = livePrice != null ? livePrice : (asset.getCurrentPrice() != null ? asset.getCurrentPrice() : asset.getAveragePrice());

        BigDecimal totalValue = asset.getQuantity().multiply(currentPrice);
        BigDecimal totalCost = asset.getQuantity().multiply(asset.getAveragePrice());
        BigDecimal gainLoss = totalValue.subtract(totalCost);

        BigDecimal gainLossPct = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            gainLossPct = gainLoss.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        return AssetDTO.builder()
                .id(asset.getId())
                .ticker(asset.getTicker())
                .name(asset.getName())
                .category(asset.getCategory())
                .quantity(asset.getQuantity())
                .averagePrice(asset.getAveragePrice())
                .currentPrice(currentPrice)
                .totalValue(totalValue)
                .gainLoss(gainLoss)
                .gainLossPercentage(gainLossPct)
                .build();
    }

    private AssetDTO mapToAssetDTOWithFixedPrice(Asset asset, BigDecimal fixedPrice) {
        BigDecimal totalValue = asset.getQuantity().multiply(fixedPrice);
        BigDecimal totalCost = asset.getQuantity().multiply(asset.getAveragePrice());
        BigDecimal gainLoss = totalValue.subtract(totalCost);

        BigDecimal gainLossPct = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            gainLossPct = gainLoss.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        return AssetDTO.builder()
                .id(asset.getId())
                .ticker(asset.getTicker())
                .name(asset.getName())
                .category(asset.getCategory())
                .quantity(asset.getQuantity())
                .averagePrice(asset.getAveragePrice())
                .currentPrice(fixedPrice)
                .totalValue(totalValue)
                .gainLoss(gainLoss)
                .gainLossPercentage(gainLossPct)
                .build();
    }
}
