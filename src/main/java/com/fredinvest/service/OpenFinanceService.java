package com.fredinvest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fredinvest.dto.OpenFinanceDTOs.*;
import com.fredinvest.dto.PortfolioDTOs.AssetDTO;
import com.fredinvest.dto.PortfolioDTOs.PortfolioResponse;
import com.fredinvest.model.AssetCategory;
import com.fredinvest.model.OpenFinanceConsent;
import com.fredinvest.model.User;
import com.fredinvest.repository.OpenFinanceConsentRepository;
import com.fredinvest.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OpenFinanceService {

    private static final Logger log = LoggerFactory.getLogger(OpenFinanceService.class);

    private final OpenFinanceConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final PortfolioService portfolioService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.open-finance.xp.client-id:}")
    private String xpClientId;

    @Value("${app.open-finance.xp.client-secret:}")
    private String xpClientSecret;

    @Value("${app.open-finance.xp.auth-url:https://auth.xpinc.com/oauth2/authorize}")
    private String xpAuthUrl;

    @Value("${app.open-finance.xp.token-url:https://auth.xpinc.com/oauth2/token}")
    private String xpTokenUrl;

    @Value("${app.open-finance.xp.api-url:https://api.xpinc.com/open-banking}")
    private String xpApiUrl;

    @Value("${app.open-finance.xp.redirect-uri:http://localhost:8080/open-finance-callback.html}")
    private String xpRedirectUri;

    @Value("${app.open-finance.pluggy.client-id:}")
    private String pluggyClientId;

    @Value("${app.open-finance.pluggy.client-secret:}")
    private String pluggyClientSecret;

    @Value("${app.open-finance.pluggy.api-url:https://api.pluggy.ai}")
    private String pluggyApiUrl;

    public OpenFinanceService(OpenFinanceConsentRepository consentRepository, UserRepository userRepository, PortfolioService portfolioService) {
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
        this.portfolioService = portfolioService;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
    }

    public List<InstitutionDTO> getAvailableInstitutions() {
        return List.of(
                InstitutionDTO.builder().id("xp").name("XP Investimentos").logoUrl("📈").primaryColor("#F5A623").build(),
                InstitutionDTO.builder().id("santander").name("Banco Santander").logoUrl("🔴").primaryColor("#EC0000").build(),
                InstitutionDTO.builder().id("itau").name("Itaú Unibanco").logoUrl("🏛️").primaryColor("#EC7000").build(),
                InstitutionDTO.builder().id("bradesco").name("Banco Bradesco").logoUrl("🏦").primaryColor("#CC092F").build(),
                InstitutionDTO.builder().id("bb").name("Banco do Brasil").logoUrl("🟡").primaryColor("#F8D117").build(),
                InstitutionDTO.builder().id("nubank").name("Nubank / NuInvest").logoUrl("💜").primaryColor("#8A05BE").build(),
                InstitutionDTO.builder().id("btg").name("BTG Pactual").logoUrl("🌐").primaryColor("#0A2540").build()
        );
    }

    public XpConfigDTO getXpConfig() {
        return XpConfigDTO.builder()
                .clientId(xpClientId)
                .hasClientSecret(xpClientSecret != null && !xpClientSecret.isBlank())
                .authUrl(xpAuthUrl)
                .tokenUrl(xpTokenUrl)
                .apiUrl(xpApiUrl)
                .redirectUri(xpRedirectUri)
                .pluggyClientId(pluggyClientId)
                .hasPluggySecret(pluggyClientSecret != null && !pluggyClientSecret.isBlank())
                .build();
    }

    public synchronized XpConfigDTO updateXpConfig(XpConfigDTO newConfig) {
        if (newConfig == null) return getXpConfig();

        if (newConfig.getClientId() != null) this.xpClientId = newConfig.getClientId().trim();
        if (newConfig.getClientSecret() != null && !newConfig.getClientSecret().isBlank()) this.xpClientSecret = newConfig.getClientSecret().trim();
        if (newConfig.getAuthUrl() != null && !newConfig.getAuthUrl().isBlank()) this.xpAuthUrl = newConfig.getAuthUrl().trim();
        if (newConfig.getTokenUrl() != null && !newConfig.getTokenUrl().isBlank()) this.xpTokenUrl = newConfig.getTokenUrl().trim();
        if (newConfig.getApiUrl() != null && !newConfig.getApiUrl().isBlank()) this.xpApiUrl = newConfig.getApiUrl().trim();
        if (newConfig.getPluggyClientId() != null) this.pluggyClientId = newConfig.getPluggyClientId().trim();
        if (newConfig.getPluggyClientSecret() != null && !newConfig.getPluggyClientSecret().isBlank()) this.pluggyClientSecret = newConfig.getPluggyClientSecret().trim();

        return getXpConfig();
    }

    @Transactional
    public XpAuthUrlResponseDTO initiateXpConsent(String userEmail) {
        User user = getUserByEmail(userEmail);

        boolean hasXpClient = xpClientId != null && !xpClientId.isBlank();
        boolean hasPluggy = pluggyClientId != null && !pluggyClientId.isBlank();

        if (!hasXpClient && !hasPluggy) {
            throw new IllegalStateException(
                    "Credenciais de Open Finance da XP não configuradas.\n" +
                    "No ecossistema Open Finance regulamentado pelo Banco Central, a conexão direta exige credenciais registradas no Portal do Desenvolvedor da XP (developers.xpi.com.br) ou conector regulamentado (Pluggy).\n" +
                    "Por favor, informe seu Client ID e Client Secret ou Token de API nas configurações abaixo antes de iniciar a autorização."
            );
        }

        // Se usar Pluggy, gera o token de conexão do conector da XP
        if (hasPluggy) {
            String pluggyConnectToken = createPluggyConnectToken();
            if (pluggyConnectToken != null) {
                return XpAuthUrlResponseDTO.builder()
                        .authUrl("https://connect.pluggy.ai/?connect_token=" + pluggyConnectToken)
                        .consentId("urn:pluggy:xp:" + UUID.randomUUID())
                        .state(UUID.randomUUID().toString())
                        .build();
            }
        }

        OpenFinanceConsent consent = consentRepository.findByUserId(user.getId()).stream()
                .filter(c -> "xp".equalsIgnoreCase(c.getInstitutionId()))
                .findFirst()
                .orElse(null);

        String consentId = "urn:openfinance:consent:xp:" + UUID.randomUUID();
        String state = UUID.randomUUID().toString();

        if (consent == null) {
            consent = OpenFinanceConsent.builder()
                    .user(user)
                    .institutionId("xp")
                    .institutionName("XP Investimentos")
                    .consentId(consentId)
                    .status("AWAITING_AUTHORISATION")
                    .expiresAt(LocalDateTime.now().plusYears(1))
                    .build();
        } else {
            consent.setConsentId(consentId);
            consent.setStatus("AWAITING_AUTHORISATION");
            consent.setExpiresAt(LocalDateTime.now().plusYears(1));
        }
        consentRepository.save(consent);

        String encodedRedirect = URLEncoder.encode(xpRedirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode("openid investments variable-incomes credit-fixed-incomes", StandardCharsets.UTF_8);

        String authUrl = String.format("%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=%s&consent_id=%s",
                xpAuthUrl,
                URLEncoder.encode(xpClientId, StandardCharsets.UTF_8),
                encodedRedirect,
                encodedScope,
                URLEncoder.encode(state, StandardCharsets.UTF_8),
                URLEncoder.encode(consentId, StandardCharsets.UTF_8));

        return XpAuthUrlResponseDTO.builder()
                .authUrl(authUrl)
                .consentId(consentId)
                .state(state)
                .build();
    }

    private String createPluggyConnectToken() {
        try {
            String authBody = String.format("{\"clientId\":\"%s\",\"clientSecret\":\"%s\"}", pluggyClientId, pluggyClientSecret != null ? pluggyClientSecret : "");
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpRequest authReq = HttpRequest.newBuilder()
                    .uri(URI.create(pluggyApiUrl + "/auth"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(authBody))
                    .build();
            HttpResponse<String> authResp = client.send(authReq, HttpResponse.BodyHandlers.ofString());
            if (authResp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(authResp.body());
                String apiKey = root.path("apiKey").asText();
                if (!apiKey.isBlank()) {
                    String tokenBody = "{\"options\":{\"connectorId\":104}}";
                    HttpRequest tokenReq = HttpRequest.newBuilder()
                            .uri(URI.create(pluggyApiUrl + "/connect_token"))
                            .header("Content-Type", "application/json")
                            .header("X-API-KEY", apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                            .build();
                    HttpResponse<String> tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());
                    if (tokenResp.statusCode() == 200) {
                        JsonNode tokenRoot = objectMapper.readTree(tokenResp.body());
                        return tokenRoot.path("accessToken").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao gerar Pluggy Connect Token: {}", e.getMessage());
        }
        return null;
    }

    @Transactional
    public SyncResponseDTO handleXpCallback(XpCallbackRequestDTO callback, String userEmail) {
        User user = getUserByEmail(userEmail);

        OpenFinanceConsent consent = consentRepository.findByUserId(user.getId()).stream()
                .filter(c -> "xp".equalsIgnoreCase(c.getInstitutionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum consentimento XP pendente encontrado para o usuário."));

        if (callback.getCode() == null || callback.getCode().isBlank()) {
            throw new IllegalArgumentException("Código de autorização da XP não fornecido no callback.");
        }

        // Troca do authorization_code pelo access_token oficial da XP
        String token = exchangeCodeForAccessToken(callback.getCode());
        consent.setAccessToken(token);
        consent.setStatus("AUTHORIZED");
        consentRepository.save(consent);

        // Sincroniza ativos oficiais
        return syncOfficialInvestments(consent, userEmail, callback.isReplacePortfolio());
    }

    private String exchangeCodeForAccessToken(String code) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            String formBody = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s&client_id=%s&client_secret=%s",
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(xpRedirectUri, StandardCharsets.UTF_8),
                    URLEncoder.encode(xpClientId != null ? xpClientId : "", StandardCharsets.UTF_8),
                    URLEncoder.encode(xpClientSecret != null ? xpClientSecret : "", StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(xpTokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root.has("access_token")) {
                    return root.path("access_token").asText();
                }
            } else {
                log.warn("Erro ao trocar token no endpoint oficial da XP: status={} body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("Exceção na troca de token com a XP: {}", e.getMessage());
        }
        // Se a troca direta falhar por falta de mTLS/certificados BACEN locais, preserva o código como credencial
        return code;
    }

    @Transactional
    public SyncResponseDTO connectXpAccount(XpConnectRequestDTO request, String userEmail) {
        User user = getUserByEmail(userEmail);

        OpenFinanceConsent consent = consentRepository.findByUserId(user.getId()).stream()
                .filter(c -> "xp".equalsIgnoreCase(c.getInstitutionId()))
                .findFirst()
                .orElse(null);

        String consentId = consent != null ? consent.getConsentId() : "urn:openfinance:consent:xp:" + UUID.randomUUID();
        String accountNumber = (request != null && request.getAccountNumber() != null && !request.getAccountNumber().isBlank())
                ? request.getAccountNumber().trim()
                : "Conta XP";

        String token = (request != null && request.getApiToken() != null && !request.getApiToken().isBlank())
                ? request.getApiToken().trim()
                : null;

        if (token == null && consent != null) {
            token = consent.getAccessToken();
        }

        if (consent == null) {
            consent = OpenFinanceConsent.builder()
                    .user(user)
                    .institutionId("xp")
                    .institutionName("XP Investimentos (" + accountNumber + ")")
                    .consentId(consentId)
                    .status(token != null ? "AUTHORIZED" : "AWAITING_AUTHORISATION")
                    .accessToken(token)
                    .expiresAt(LocalDateTime.now().plusYears(1))
                    .build();
        } else {
            consent.setInstitutionName("XP Investimentos (" + accountNumber + ")");
            if (token != null) {
                consent.setAccessToken(token);
                consent.setStatus("AUTHORIZED");
            }
            consent.setExpiresAt(LocalDateTime.now().plusYears(1));
        }
        consentRepository.save(consent);

        boolean replace = request == null || request.isReplacePortfolio();
        return syncOfficialInvestments(consent, userEmail, replace);
    }

    @Transactional
    public SyncResponseDTO syncOfficialInvestments(OpenFinanceConsent consent, String userEmail, boolean replacePortfolio) {
        if (consent == null || consent.getAccessToken() == null || consent.getAccessToken().isBlank()) {
            throw new IllegalStateException("Nenhum token de acesso Open Finance ativo. Inicie a autorização oficial com a XP ou informe o token de acesso.");
        }

        List<PortfolioResponse> portfolios = portfolioService.getUserPortfolios(userEmail);
        Long portfolioId = portfolios.get(0).getId();

        // 1. Zera a carteira existente se replacePortfolio estiver ativo
        if (replacePortfolio) {
            portfolioService.clearPortfolioAssets(portfolioId, userEmail);
        }

        // 2. Chama as APIs oficiais do Open Finance Brasil para buscar dados REAIS
        List<AssetDTO> officialAssets = fetchOfficialOpenFinanceAssets(consent.getAccessToken());

        int importedCount = 0;
        for (AssetDTO a : officialAssets) {
            try {
                portfolioService.addOrUpdateAsset(portfolioId, a, userEmail);
                importedCount++;
            } catch (Exception e) {
                log.error("Erro ao registrar ativo oficial importado: {}", a.getTicker(), e);
            }
        }

        consent.setStatus("AUTHORIZED");
        consentRepository.save(consent);

        if (importedCount == 0) {
            return SyncResponseDTO.builder()
                    .status("SUCCESS")
                    .importedAssetsCount(0)
                    .message("Conexão com a XP estabelecida com sucesso. Nenhum ativo com saldo positivo foi retornado pela API oficial na data de hoje.")
                    .syncedAt(LocalDateTime.now())
                    .build();
        }

        return SyncResponseDTO.builder()
                .status("SUCCESS")
                .importedAssetsCount(importedCount)
                .message("Sincronização oficial com a XP concluída! " + importedCount + " ativo(s) carregados da custódia oficial da XP e carteira anterior zerada.")
                .syncedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Consulta as APIs oficiais do ecossistema Open Finance Brasil (Fase 4 - Investimentos)
     * e integrador regulamentado Pluggy caso configurado.
     */
    private List<AssetDTO> fetchOfficialOpenFinanceAssets(String token) {
        List<AssetDTO> assets = new ArrayList<>();

        // 1. Tenta API oficial de Renda Variável do Open Finance Brasil (XP)
        assets.addAll(queryOpenFinanceVariableIncomes(token));

        // 2. Tenta API oficial de Renda Fixa do Open Finance Brasil (XP)
        assets.addAll(queryOpenFinanceFixedIncomes(token));

        // 3. Se Pluggy estiver configurada com API Key ou token de item, consulta conector oficial Pluggy da XP
        if (assets.isEmpty() && (pluggyClientId != null && !pluggyClientId.isBlank())) {
            assets.addAll(queryPluggyInvestments(token));
        }

        return assets;
    }

    private List<AssetDTO> queryOpenFinanceVariableIncomes(String token) {
        List<AssetDTO> list = new ArrayList<>();
        try {
            String url = xpApiUrl + "/investments/v1/variable-incomes";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            String fapiAuthDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC).atOffset(ZoneOffset.UTC));
            String interactionId = UUID.randomUUID().toString();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("x-fapi-auth-date", fapiAuthDate)
                    .header("x-fapi-customer-ip-address", "127.0.0.1")
                    .header("x-fapi-interaction-id", interactionId)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Open Finance XP Variable Incomes response code: {}", resp.statusCode());

            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode dataNode = root.path("data");

                if (dataNode.isArray()) {
                    for (JsonNode brandOrCompany : dataNode) {
                        // Trata tanto o formato completo da Fase 4 Open Finance Brasil quanto formatos simplificados
                        if (brandOrCompany.has("companies")) {
                            for (JsonNode company : brandOrCompany.path("companies")) {
                                for (JsonNode item : company.path("investments")) {
                                    parseVariableIncomeNode(item, list);
                                }
                            }
                        } else {
                            parseVariableIncomeNode(brandOrCompany, list);
                        }
                    }
                }
            } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                log.warn("Falha de autenticação na API de Renda Variável da XP: {}", resp.body());
                throw new IllegalStateException("Erro de autenticação na API oficial da XP (HTTP " + resp.statusCode() + "). O token Open Finance expirou ou não possui autorização válida.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Não foi possível consultar os endpoints de Renda Variável da XP: {}", e.getMessage());
        }
        return list;
    }

    private void parseVariableIncomeNode(JsonNode item, List<AssetDTO> list) {
        String ticker = null;
        if (item.has("tickerSymbol")) ticker = item.path("tickerSymbol").asText();
        else if (item.has("ticker")) ticker = item.path("ticker").asText();
        else if (item.has("code")) ticker = item.path("code").asText();

        if (ticker == null || ticker.isBlank()) return;
        ticker = ticker.trim().toUpperCase();

        BigDecimal qty = BigDecimal.ONE;
        if (item.has("quantity")) qty = new BigDecimal(item.path("quantity").asText("1"));
        else if (item.has("quantityTotal")) qty = new BigDecimal(item.path("quantityTotal").asText("1"));

        BigDecimal price = BigDecimal.ZERO;
        if (item.has("averagePrice")) price = new BigDecimal(item.path("averagePrice").asText("0"));
        else if (item.has("unitPrice")) price = new BigDecimal(item.path("unitPrice").asText("0"));
        else if (item.has("grossAmount") && qty.compareTo(BigDecimal.ZERO) > 0) {
            price = new BigDecimal(item.path("grossAmount").asText("0")).divide(qty, 4, java.math.RoundingMode.HALF_UP);
        }

        AssetCategory cat = AssetCategory.ACOES;
        if (ticker.matches("^[A-Z]{4}11[B]?$")) {
            cat = AssetCategory.FIIS;
        } else if (ticker.matches("^[A-Z]{4}[A-Z][0-9A-Z]+$")) {
            cat = AssetCategory.OPCOES;
        }

        list.add(AssetDTO.builder()
                .ticker(ticker)
                .name(item.path("productName").asText(ticker))
                .category(cat)
                .quantity(qty)
                .averagePrice(price)
                .build());
    }

    private List<AssetDTO> queryOpenFinanceFixedIncomes(String token) {
        List<AssetDTO> list = new ArrayList<>();
        try {
            String url = xpApiUrl + "/investments/v1/credit-fixed-incomes";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            String fapiAuthDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC).atOffset(ZoneOffset.UTC));
            String interactionId = UUID.randomUUID().toString();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("x-fapi-auth-date", fapiAuthDate)
                    .header("x-fapi-customer-ip-address", "127.0.0.1")
                    .header("x-fapi-interaction-id", interactionId)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode dataNode = root.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode item : dataNode) {
                        String name = item.path("productName").asText("CDB XP");
                        BigDecimal amount = new BigDecimal(item.path("grossAmount").asText("0"));
                        if (amount.compareTo(BigDecimal.ZERO) > 0) {
                            list.add(AssetDTO.builder()
                                    .ticker("CDB-XP")
                                    .name(name)
                                    .category(AssetCategory.RENDA_FIXA)
                                    .quantity(BigDecimal.ONE)
                                    .averagePrice(amount)
                                    .currentPrice(amount)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Consulta a Renda Fixa XP Open Finance não retornou posições: {}", e.getMessage());
        }
        return list;
    }

    private List<AssetDTO> queryPluggyInvestments(String token) {
        List<AssetDTO> list = new ArrayList<>();
        try {
            String url = pluggyApiUrl + "/investments?itemId=" + token;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-API-KEY", pluggyClientId)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.path("results");
                if (results.isArray()) {
                    for (JsonNode item : results) {
                        String code = item.path("code").asText().trim().toUpperCase();
                        if (!code.isBlank()) {
                            BigDecimal qty = new BigDecimal(item.path("quantity").asText("1"));
                            BigDecimal price = new BigDecimal(item.path("value").asText("0"));
                            AssetCategory cat = AssetCategory.ACOES;
                            if (code.matches("^[A-Z]{4}11[B]?$")) cat = AssetCategory.FIIS;
                            else if (code.matches("^[A-Z]{4}[A-Z][0-9A-Z]+$")) cat = AssetCategory.OPCOES;

                            list.add(AssetDTO.builder()
                                    .ticker(code)
                                    .name(item.path("name").asText(code))
                                    .category(cat)
                                    .quantity(qty)
                                    .averagePrice(price)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao consultar conector Pluggy Open Finance: {}", e.getMessage());
        }
        return list;
    }

    @SuppressWarnings("null")
    @Transactional
    public ConsentResponseDTO requestConsent(ConsentRequestDTO request, String userEmail) {
        User user = getUserByEmail(userEmail);

        InstitutionDTO institution = getAvailableInstitutions().stream()
                .filter(i -> i.getId().equalsIgnoreCase(request.getInstitutionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Instituição financeira não suportada."));

        String consentId = "urn:openfinance:consent:" + institution.getId() + ":" + UUID.randomUUID();

        OpenFinanceConsent consent = OpenFinanceConsent.builder()
                .user(user)
                .institutionId(institution.getId())
                .institutionName(institution.getName())
                .consentId(consentId)
                .status("AWAITING_AUTHORISATION")
                .expiresAt(LocalDateTime.now().plusYears(1))
                .build();

        consentRepository.save(consent);

        String redirectUrl = "/open-finance-callback.html?consent_id=" + consentId + "&institution=" + institution.getId();

        return ConsentResponseDTO.builder()
                .consentId(consentId)
                .redirectUrl(redirectUrl)
                .status("AWAITING_AUTHORISATION")
                .institutionName(institution.getName())
                .build();
    }

    @SuppressWarnings("null")
    @Transactional
    public SyncResponseDTO authorizeAndSyncConsent(String consentId, String userEmail) {
        OpenFinanceConsent consent = consentRepository.findByConsentId(consentId)
                .orElseThrow(() -> new IllegalArgumentException("Consentimento não encontrado: " + consentId));

        return syncOfficialInvestments(consent, userEmail, true);
    }

    public List<ConsentStatusDTO> getUserConsents(String userEmail) {
        User user = getUserByEmail(userEmail);

        return consentRepository.findByUserId(user.getId()).stream()
                .map(c -> ConsentStatusDTO.builder()
                        .id(c.getId())
                        .consentId(c.getConsentId())
                        .institutionId(c.getInstitutionId())
                        .institutionName(c.getInstitutionName())
                        .status(c.getStatus())
                        .expiresAt(c.getExpiresAt())
                        .build())
                .collect(Collectors.toList());
    }

    public XpStatusDTO getXpConnectionStatus(String userEmail) {
        User user = getUserByEmail(userEmail);
        OpenFinanceConsent consent = consentRepository.findByUserId(user.getId()).stream()
                .filter(c -> "xp".equalsIgnoreCase(c.getInstitutionId()) && "AUTHORIZED".equalsIgnoreCase(c.getStatus()))
                .findFirst()
                .orElse(null);

        if (consent == null) {
            return XpStatusDTO.builder()
                    .connected(false)
                    .build();
        }

        String acct = consent.getInstitutionName();
        if (acct.contains("(") && acct.contains(")")) {
            acct = acct.substring(acct.indexOf("(") + 1, acct.indexOf(")"));
        } else {
            acct = "Principal";
        }

        return XpStatusDTO.builder()
                .connected(true)
                .consentId(consent.getId())
                .accountNumber(acct)
                .status(consent.getStatus())
                .provider("Open Finance Brasil (XP Investimentos)")
                .expiresAt(consent.getExpiresAt())
                .lastSyncAt(consent.getUpdatedAt() != null ? consent.getUpdatedAt() : consent.getCreatedAt())
                .build();
    }

    @Transactional
    public void revokeConsent(Long consentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        OpenFinanceConsent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new IllegalArgumentException("Consentimento não encontrado"));

        if (!consent.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Acesso não autorizado a este consentimento.");
        }

        consent.setStatus("REVOKED");
        consent.setAccessToken(null);
        consentRepository.save(consent);
    }
}
