package com.fredinvest.service;

import com.fredinvest.dto.AssetTransactionDTO;
import com.fredinvest.dto.PortfolioDTOs.*;
import com.fredinvest.model.Asset;
import com.fredinvest.model.AssetCategory;
import com.fredinvest.model.AssetTransaction;
import com.fredinvest.model.Portfolio;
import com.fredinvest.model.User;
import com.fredinvest.repository.AssetRepository;
import com.fredinvest.repository.AssetTransactionRepository;
import com.fredinvest.repository.PortfolioRepository;
import com.fredinvest.repository.UserRepository;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final AssetTransactionRepository assetTransactionRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, AssetRepository assetRepository, UserRepository userRepository, AssetTransactionRepository assetTransactionRepository) {
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.assetTransactionRepository = assetTransactionRepository;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
    }

	@SuppressWarnings("null")
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

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
	@Transactional
    public AssetDTO addOrUpdateAsset(@NonNull Long portfolioId, AssetDTO assetDTO, String userEmail) {
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
            if (assetDTO.getStrikePrice() != null) {
                asset.setStrikePrice(assetDTO.getStrikePrice());
            }
            if (assetDTO.getExpirationDate() != null) {
                asset.setExpirationDate(assetDTO.getExpirationDate());
            }
            if (assetDTO.getUnderlyingTicker() != null) {
                asset.setUnderlyingTicker(assetDTO.getUnderlyingTicker());
            } else if (asset.getCategory() == AssetCategory.OPCOES && asset.getUnderlyingTicker() == null) {
                asset.setUnderlyingTicker(determineUnderlyingTicker(asset.getTicker(), null));
            }
            
            // Calculate new average price and quantity
            BigDecimal oldTotal = asset.getQuantity().multiply(asset.getAveragePrice());
            BigDecimal addedTotal = assetDTO.getQuantity().multiply(assetDTO.getAveragePrice());
            BigDecimal newQuantity = asset.getQuantity().add(assetDTO.getQuantity());
            BigDecimal newAvg = oldTotal.add(addedTotal).divide(newQuantity, 4, RoundingMode.HALF_UP);

            asset.setQuantity(newQuantity);
            asset.setAveragePrice(newAvg);
            asset.setCurrentPrice(currentPrice);
        } else {
            String underlyingTicker = assetDTO.getUnderlyingTicker() != null
                    ? assetDTO.getUnderlyingTicker()
                    : (assetDTO.getCategory() == AssetCategory.OPCOES ? determineUnderlyingTicker(assetDTO.getTicker().toUpperCase(), null) : null);

            asset = Asset.builder()
                    .ticker(assetDTO.getTicker().toUpperCase())
                    .name(assetDTO.getName())
                    .category(assetDTO.getCategory())
                    .quantity(assetDTO.getQuantity())
                    .averagePrice(assetDTO.getAveragePrice())
                    .currentPrice(currentPrice)
                    .strikePrice(assetDTO.getStrikePrice())
                    .expirationDate(assetDTO.getExpirationDate())
                    .underlyingTicker(underlyingTicker)
                    .portfolio(portfolio)
                    .build();
        }

        asset = assetRepository.save(asset);

        // Registrar no histórico de compras
        BigDecimal purchaseQty = assetDTO.getQuantity();
        BigDecimal purchasePrice = assetDTO.getAveragePrice();
        LocalDateTime txDate = assetDTO.getPurchaseDate() != null
                ? assetDTO.getPurchaseDate().atTime(java.time.LocalTime.now())
                : LocalDateTime.now();

        AssetTransaction transaction = AssetTransaction.builder()
                .asset(asset)
                .quantity(purchaseQty)
                .price(purchasePrice)
                .totalValue(purchaseQty.multiply(purchasePrice))
                .transactionDate(txDate)
                .build();
        assetTransactionRepository.save(transaction);

        return mapToAssetDTO(asset);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PortfolioService.class);
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public static class LiveQuote {
        private final BigDecimal price;
        private final String source;
        private final String name;
        private final AssetCategory category;
        private final BigDecimal strikePrice;
        private final java.time.LocalDate expirationDate;
        private final String underlyingTicker;
        private final BigDecimal underlyingPrice;

        public LiveQuote(BigDecimal price, String source, String name, AssetCategory category) {
            this(price, source, name, category, null, null, null, null);
        }

        public LiveQuote(BigDecimal price, String source, String name, AssetCategory category, BigDecimal strikePrice, java.time.LocalDate expirationDate) {
            this(price, source, name, category, strikePrice, expirationDate, null, null);
        }

        public LiveQuote(BigDecimal price, String source, String name, AssetCategory category, BigDecimal strikePrice, java.time.LocalDate expirationDate, String underlyingTicker, BigDecimal underlyingPrice) {
            this.price = price;
            this.source = source;
            this.name = name;
            this.category = category;
            this.strikePrice = strikePrice;
            this.expirationDate = expirationDate;
            this.underlyingTicker = underlyingTicker;
            this.underlyingPrice = underlyingPrice;
        }

        public BigDecimal getPrice() { return price; }
        public String getSource() { return source; }
        public String getName() { return name; }
        public AssetCategory getCategory() { return category; }
        public BigDecimal getStrikePrice() { return strikePrice; }
        public java.time.LocalDate getExpirationDate() { return expirationDate; }
        public String getUnderlyingTicker() { return underlyingTicker; }
        public BigDecimal getUnderlyingPrice() { return underlyingPrice; }
    }

    public BigDecimal fetchLivePricePublic(String ticker, AssetCategory category) {
        return fetchLivePrice(ticker, category);
    }

    public LiveQuote fetchLiveQuote(String ticker, AssetCategory category) {
        if (ticker == null || ticker.trim().isBlank()) {
            return null;
        }

        String cleanTicker = ticker.trim().toUpperCase();

        // Se a categoria for OPCOES ou o ticker tiver formato de opção/ativo da B3, consulta a B3 primeiro
        if (category == AssetCategory.OPCOES || cleanTicker.matches("^[A-Z]{4}[A-Z][0-9A-Z]+$")) {
            LiveQuote b3Quote = tryFetchFromB3(cleanTicker);
            if (b3Quote != null) {
                log.info("Cotação obtida da B3 para Opção '{}': R$ {}", cleanTicker, b3Quote.getPrice());
                return b3Quote;
            }
        }

        if ((category == AssetCategory.ACOES || category == AssetCategory.FIIS) && !cleanTicker.contains("-") && !cleanTicker.endsWith(".SA")) {
            LiveQuote b3Quote = tryFetchFromB3(cleanTicker);
            if (b3Quote != null) {
                log.info("Cotação obtida da B3 para '{}': R$ {}", cleanTicker, b3Quote.getPrice());
                return b3Quote;
            }
        }

        // Yahoo Finance com múltiplos candidatos
        List<String> candidates = new ArrayList<>();
        if (cleanTicker.endsWith(".SA")) {
            candidates.add(cleanTicker);
            candidates.add(cleanTicker.substring(0, cleanTicker.length() - 3));
        } else {
            if (category == AssetCategory.ACOES || category == AssetCategory.FIIS || category == AssetCategory.OPCOES) {
                candidates.add(cleanTicker + ".SA");
                candidates.add(cleanTicker);
            } else if (category == AssetCategory.CRIPTO) {
                candidates.add(cleanTicker + "-USD");
                candidates.add(cleanTicker + "-BRL");
                candidates.add(cleanTicker);
            } else {
                candidates.add(cleanTicker);
                candidates.add(cleanTicker + ".SA");
            }
        }

        for (String candidate : candidates) {
            BigDecimal price = tryFetchFromYahoo(candidate);
            if (price != null) {
                log.info("Cotação obtida do Yahoo Finance para '{}' (candidato '{}'): R$ {}", ticker, candidate, price);
                AssetCategory detectedCategory = category;
                if (cleanTicker.matches("^[A-Z]{4}[A-Z][0-9A-Z]+$")) {
                    detectedCategory = AssetCategory.OPCOES;
                } else if (cleanTicker.matches("^[A-Z]{4}11(\\.SA)?$")) {
                    detectedCategory = AssetCategory.FIIS;
                } else if (cleanTicker.matches("^[A-Z]{4}[3-6](\\.SA)?$") || !cleanTicker.contains("-")) {
                    detectedCategory = AssetCategory.ACOES;
                } else if (cleanTicker.contains("-USD") || cleanTicker.contains("-BRL")) {
                    detectedCategory = AssetCategory.CRIPTO;
                }
                return new LiveQuote(price, "Yahoo Finance", null, detectedCategory);
            }
        }

        // Fallback final: tenta B3 caso ainda não tenha consultado
        LiveQuote fallbackB3 = tryFetchFromB3(cleanTicker.replace(".SA", ""));
        if (fallbackB3 != null) {
            log.info("Cotação obtida da B3 (fallback) para '{}': R$ {}", cleanTicker, fallbackB3.getPrice());
            return fallbackB3;
        }

        log.warn("Nenhuma cotação encontrada na B3 ou Yahoo Finance para o ticker: '{}'", ticker);
        return null;
    }

    private BigDecimal fetchLivePrice(String ticker, AssetCategory category) {
        LiveQuote quote = fetchLiveQuote(ticker, category);
        return quote != null ? quote.getPrice() : null;
    }

    private LiveQuote tryFetchFromB3(String queryTicker) {
        try {
            String url = "https://cotacao.b3.com.br/mds/api/v1/InstrumentQuotation/" + queryTicker;
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(4))
                    .build();

            java.net.http.HttpResponse<String> resp = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                if ("OK".equalsIgnoreCase(root.path("BizSts").path("cd").asText())) {
                    com.fasterxml.jackson.databind.JsonNode trad = root.path("Trad");
                    if (trad.isArray() && trad.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode scty = trad.get(0).path("scty");
                        com.fasterxml.jackson.databind.JsonNode sctyQtn = scty.path("SctyQtn");
                        String desc = scty.hasNonNull("desc") ? scty.path("desc").asText() : null;

                        String mktNm = scty.has("mkt") ? scty.path("mkt").path("nm").asText() : "";
                        AssetCategory detectedCategory = null;
                        if ("Opcoes".equalsIgnoreCase(mktNm) || queryTicker.matches("^[A-Z]{4}[A-Z][0-9A-Z]+$")) {
                            detectedCategory = AssetCategory.OPCOES;
                        } else if (queryTicker.matches("^[A-Z]{4}11$")) {
                            detectedCategory = AssetCategory.FIIS;
                        } else if ("Vista".equalsIgnoreCase(mktNm) || queryTicker.matches("^[A-Z]{4}[3-6]$")) {
                            detectedCategory = AssetCategory.ACOES;
                        }

                        BigDecimal price = null;
                        if (sctyQtn.hasNonNull("curPrc") && sctyQtn.path("curPrc").asDouble() > 0) {
                            price = BigDecimal.valueOf(sctyQtn.path("curPrc").asDouble()).setScale(2, RoundingMode.HALF_UP);
                        } else if (sctyQtn.hasNonNull("avrgPric") && sctyQtn.path("avrgPric").asDouble() > 0) {
                            price = BigDecimal.valueOf(sctyQtn.path("avrgPric").asDouble()).setScale(2, RoundingMode.HALF_UP);
                        } else if (sctyQtn.hasNonNull("opngPric") && sctyQtn.path("opngPric").asDouble() > 0) {
                            price = BigDecimal.valueOf(sctyQtn.path("opngPric").asDouble()).setScale(2, RoundingMode.HALF_UP);
                        } else if (sctyQtn.hasNonNull("minPric") && sctyQtn.path("minPric").asDouble() > 0) {
                            price = BigDecimal.valueOf(sctyQtn.path("minPric").asDouble()).setScale(2, RoundingMode.HALF_UP);
                        }

                        BigDecimal strike = null;
                        java.time.LocalDate expiration = null;
                        String underlyingTicker = null;
                        BigDecimal underlyingPrice = null;
                        if (detectedCategory == AssetCategory.OPCOES) {
                            if (desc != null) {
                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+[\\.,][0-9]+)\\s*$").matcher(desc.trim());
                                if (m.find()) {
                                    try {
                                        strike = new BigDecimal(m.group(1).replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
                                    } catch (Exception ignored) {}
                                }
                            }
                            expiration = calculateB3OptionExpiration(queryTicker);
                            String[] holder = new String[]{null};
                            underlyingPrice = fetchUnderlyingStockPrice(holder, queryTicker, desc);
                            underlyingTicker = holder[0];
                        }

                        if (price != null) {
                            return new LiveQuote(price, "B3", desc, detectedCategory, strike, expiration, underlyingTicker, underlyingPrice);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Erro ao consultar B3 para {}: {}", queryTicker, e.getMessage());
        }
        return null;
    }

    public static String determineUnderlyingTicker(String optionTicker, String desc) {
        if (optionTicker == null || optionTicker.length() < 4) return null;
        String base = optionTicker.substring(0, 4).toUpperCase();

        if (desc != null) {
            String upperDesc = desc.toUpperCase();
            if (upperDesc.contains(" ON") || upperDesc.startsWith("ON ") || upperDesc.contains(" ON ")) {
                return base + "3";
            } else if (upperDesc.contains(" PN ") || upperDesc.contains(" PN") || upperDesc.startsWith("PN ")) {
                return base + "4";
            } else if (upperDesc.contains(" PNA")) {
                return base + "5";
            } else if (upperDesc.contains(" PNB")) {
                return base + "6";
            } else if (upperDesc.contains(" UNT") || upperDesc.contains(" 11") || upperDesc.contains("11")) {
                return base + "11";
            }
        }

        return switch (base) {
            case "VALE", "BBAS", "ABEV", "MGLU", "B3SA", "PRIO", "RENT", "SUZB", "WEGE", "JBSS",
                 "CSNA", "ELET", "EMBR", "HAPV", "LREN", "VBBR", "COGN", "CYRE", "RADL", "SMTO",
                 "CSAN", "UGPA", "VIVT", "TIMS", "BBSE", "IRBR", "ALOS", "EQTL", "SBSP",
                 "NTCO", "PCAR", "ASAI", "CRFB", "BRFS", "MRFG", "BEEF", "BPAC", "AZZA" -> base + "3";
            case "PETR", "ITUB", "BBDC", "GGBR", "CMIG", "RAIZ", "AZUL", "GOLL", "ITSA" -> base + "4";
            case "USIM", "BRKM" -> base + "5";
            case "CPLE" -> base + "6";
            case "BOVA", "SMAL", "KLBN", "TAEE", "SAPR", "SANB", "ALUP" -> base + "11";
            default -> base + "4";
        };
    }

    private BigDecimal fetchUnderlyingStockPrice(String[] tickerHolder, String optionTicker, String desc) {
        String underlyingTicker = tickerHolder[0] != null ? tickerHolder[0] : determineUnderlyingTicker(optionTicker, desc);
        if (underlyingTicker == null) return null;

        BigDecimal price = fetchLivePrice(underlyingTicker, AssetCategory.ACOES);
        if (price == null && underlyingTicker.endsWith("4")) {
            String alt = underlyingTicker.substring(0, 4) + "3";
            BigDecimal altPrice = fetchLivePrice(alt, AssetCategory.ACOES);
            if (altPrice != null) {
                underlyingTicker = alt;
                price = altPrice;
            }
        } else if (price == null && underlyingTicker.endsWith("3")) {
            String alt = underlyingTicker.substring(0, 4) + "4";
            BigDecimal altPrice = fetchLivePrice(alt, AssetCategory.ACOES);
            if (altPrice != null) {
                underlyingTicker = alt;
                price = altPrice;
            }
        }
        tickerHolder[0] = underlyingTicker;
        return price;
    }

    public static java.time.LocalDate calculateB3OptionExpiration(String ticker) {
        if (ticker == null || ticker.length() < 5) return null;
        char code = Character.toUpperCase(ticker.charAt(4));
        int month = -1;
        if (code >= 'A' && code <= 'L') {
            month = code - 'A' + 1;
        } else if (code >= 'M' && code <= 'X') {
            month = code - 'M' + 1;
        }
        if (month == -1) return null;

        java.time.LocalDate now = java.time.LocalDate.now();
        int year = now.getYear();
        java.time.LocalDate thirdFriday = java.time.LocalDate.of(year, month, 1)
                .with(java.time.temporal.TemporalAdjusters.dayOfWeekInMonth(3, java.time.DayOfWeek.FRIDAY));
        if (thirdFriday.isBefore(now)) {
            thirdFriday = java.time.LocalDate.of(year + 1, month, 1)
                    .with(java.time.temporal.TemporalAdjusters.dayOfWeekInMonth(3, java.time.DayOfWeek.FRIDAY));
        }
        return thirdFriday;
    }

    private BigDecimal tryFetchFromYahoo(String queryTicker) {
        String[] hosts = {"query1.finance.yahoo.com", "query2.finance.yahoo.com"};
        for (String host : hosts) {
            try {
                String url = "https://" + host + "/v8/finance/chart/" + queryTicker;
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .timeout(java.time.Duration.ofSeconds(4))
                        .build();

                java.net.http.HttpResponse<String> resp = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                    com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                    com.fasterxml.jackson.databind.JsonNode result = root.path("chart").path("result");
                    if (result.isArray() && result.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode meta = result.get(0).path("meta");
                        if (meta.hasNonNull("regularMarketPrice")) {
                            return new BigDecimal(meta.path("regularMarketPrice").asText()).setScale(2, RoundingMode.HALF_UP);
                        } else if (meta.hasNonNull("chartPreviousClose")) {
                            return new BigDecimal(meta.path("chartPreviousClose").asText()).setScale(2, RoundingMode.HALF_UP);
                        } else if (meta.hasNonNull("previousClose")) {
                            return new BigDecimal(meta.path("previousClose").asText()).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Erro ao consultar Yahoo Finance em {} para {}: {}", host, queryTicker, e.getMessage());
            }
        }
        return null;
    }

    @Transactional
    public AssetDTO updateAssetPrice(Long portfolioId, @NonNull Long assetId, BigDecimal newPrice, String userEmail) {
        User user = getUserByEmail(userEmail);
        @SuppressWarnings("null")
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
    public void deleteAsset(@NonNull Long portfolioId, @NonNull Long assetId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Carteira não encontrada."));

        if (!portfolio.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Acesso negado a esta carteira.");
        }

        assetTransactionRepository.deleteByAssetId(assetId);
        assetRepository.deleteById(assetId);
    }

    @Transactional
    public List<AssetTransactionDTO> getAssetPurchaseHistory(Long portfolioId, @NonNull Long assetId, String userEmail) {
        User user = getUserByEmail(userEmail);
        @SuppressWarnings("null")
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

        List<AssetTransaction> transactions = assetTransactionRepository.findByAssetIdOrderByTransactionDateDesc(assetId);
        if (transactions.isEmpty()) {
            AssetTransaction initialTx = AssetTransaction.builder()
                    .asset(asset)
                    .quantity(asset.getQuantity())
                    .price(asset.getAveragePrice())
                    .totalValue(asset.getQuantity().multiply(asset.getAveragePrice()))
                    .transactionDate(asset.getCreatedAt() != null ? asset.getCreatedAt() : LocalDateTime.now())
                    .build();
            initialTx = assetTransactionRepository.save(initialTx);
            transactions = List.of(initialTx);
        }

        return transactions.stream()
                .map(tx -> new AssetTransactionDTO(
                        tx.getId(),
                        asset.getId(),
                        tx.getQuantity(),
                        tx.getPrice(),
                        tx.getTotalValue(),
                        tx.getTransactionDate()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteAssetTransaction(@NonNull Long portfolioId, @NonNull Long assetId, @NonNull Long transactionId, String userEmail) {
        User user = getUserByEmail(userEmail);
        @SuppressWarnings("null")
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

        @SuppressWarnings("null")
        AssetTransaction tx = assetTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada."));

        if (!tx.getAsset().getId().equals(asset.getId())) {
            throw new IllegalArgumentException("Transação não pertence a este ativo.");
        }

        assetTransactionRepository.delete(tx);

        List<AssetTransaction> remaining = assetTransactionRepository.findByAssetIdOrderByTransactionDateDesc(assetId);
        if (remaining.isEmpty()) {
            assetRepository.deleteById(assetId);
            return true; // Ativo excluído pois não restam compras
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (AssetTransaction t : remaining) {
            totalQty = totalQty.add(t.getQuantity());
            totalCost = totalCost.add(t.getTotalValue());
        }
        asset.setQuantity(totalQty);
        if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
            asset.setAveragePrice(totalCost.divide(totalQty, 4, RoundingMode.HALF_UP));
        }
        assetRepository.save(asset);
        return false;
    }

    @Transactional
    public List<AssetTransactionDTO> updateAssetTransactionQuantity(@NonNull Long portfolioId, @NonNull Long assetId, @NonNull Long transactionId, @NonNull BigDecimal newQuantity, String userEmail) {
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }

        User user = getUserByEmail(userEmail);
        @SuppressWarnings("null")
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

        @SuppressWarnings("null")
        AssetTransaction tx = assetTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada."));

        if (!tx.getAsset().getId().equals(asset.getId())) {
            throw new IllegalArgumentException("Transação não pertence a este ativo.");
        }

        tx.setQuantity(newQuantity);
        tx.setTotalValue(newQuantity.multiply(tx.getPrice()));
        assetTransactionRepository.save(tx);

        List<AssetTransaction> allTx = assetTransactionRepository.findByAssetIdOrderByTransactionDateDesc(assetId);
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (AssetTransaction t : allTx) {
            totalQty = totalQty.add(t.getQuantity());
            totalCost = totalCost.add(t.getTotalValue());
        }
        asset.setQuantity(totalQty);
        if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
            asset.setAveragePrice(totalCost.divide(totalQty, 4, RoundingMode.HALF_UP));
        }
        assetRepository.save(asset);

        return allTx.stream()
                .map(t -> new AssetTransactionDTO(
                        t.getId(),
                        asset.getId(),
                        t.getQuantity(),
                        t.getPrice(),
                        t.getTotalValue(),
                        t.getTransactionDate()
                ))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
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
        if (livePrice != null && !livePrice.equals(asset.getCurrentPrice())) {
            asset.setCurrentPrice(livePrice);
            try {
                assetRepository.save(asset);
            } catch (Exception ignored) {}
        }
        BigDecimal currentPrice = asset.getCurrentPrice() != null ? asset.getCurrentPrice() : asset.getAveragePrice();

        BigDecimal totalValue = asset.getQuantity().multiply(currentPrice);
        BigDecimal totalCost = asset.getQuantity().multiply(asset.getAveragePrice());
        BigDecimal gainLoss = totalValue.subtract(totalCost);

        BigDecimal gainLossPct = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            gainLossPct = gainLoss.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        String underlyingTicker = null;
        BigDecimal underlyingPrice = null;
        if (asset.getCategory() == AssetCategory.OPCOES) {
            String[] holder = new String[]{asset.getUnderlyingTicker()};
            underlyingPrice = fetchUnderlyingStockPrice(holder, asset.getTicker(), null);
            underlyingTicker = holder[0];
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
                .strikePrice(asset.getStrikePrice())
                .expirationDate(asset.getExpirationDate())
                .underlyingTicker(underlyingTicker)
                .underlyingPrice(underlyingPrice)
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

        String underlyingTicker = null;
        BigDecimal underlyingPrice = null;
        if (asset.getCategory() == AssetCategory.OPCOES) {
            String[] holder = new String[]{asset.getUnderlyingTicker()};
            underlyingPrice = fetchUnderlyingStockPrice(holder, asset.getTicker(), null);
            underlyingTicker = holder[0];
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
                .strikePrice(asset.getStrikePrice())
                .expirationDate(asset.getExpirationDate())
                .underlyingTicker(underlyingTicker)
                .underlyingPrice(underlyingPrice)
                .build();
    }
}
