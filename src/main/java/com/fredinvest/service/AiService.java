package com.fredinvest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fredinvest.dto.AiDTOs.*;
import com.fredinvest.dto.PortfolioDTOs.AssetDTO;
import com.fredinvest.dto.PortfolioDTOs.CategoryAllocationDTO;
import com.fredinvest.dto.PortfolioDTOs.PortfolioResponse;
import com.fredinvest.dto.PortfolioDTOs.PortfolioSummaryDTO;
import com.fredinvest.model.AuthProvider;
import com.fredinvest.model.User;
import com.fredinvest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiService {

    private final PortfolioService portfolioService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.api-key:mock-key}")
    private String aiApiKey;

    public AiService(PortfolioService portfolioService, UserRepository userRepository) {
        this.portfolioService = portfolioService;
        this.userRepository = userRepository;
    }

    private String[] resolveProviderAndModel(String requestedProvider, String userEmail) {
        String provider = requestedProvider != null && !requestedProvider.isBlank() 
                ? requestedProvider.toUpperCase() 
                : null;

        if (provider == null && userEmail != null) {
            User user = userRepository.findByEmail(userEmail).orElse(null);
            if (user != null && user.getProvider() == AuthProvider.GOOGLE) {
                provider = "GEMINI";
            }
        }

        if (provider == null) {
            provider = "OPENAI";
        }

        String modelName;
        switch (provider) {
            case "GEMINI":
                modelName = "Google Gemini 2.5 Flash";
                break;
            case "CLAUDE":
                modelName = "Anthropic Claude 3.5 Sonnet";
                break;
            case "DEEPSEEK":
                modelName = "DeepSeek V3";
                break;
            case "OPENAI":
            default:
                provider = "OPENAI";
                modelName = "OpenAI GPT-4o-mini";
                break;
        }

        return new String[]{provider, modelName};
    }

    public AiAnalysisResponse analyzePortfolio(AiAnalysisRequest request, String userEmail) {
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userEmail);
        String[] resolved = resolveProviderAndModel(request.getProvider(), userEmail);
        String provider = resolved[0];
        String model = resolved[1];

        String profile = request.getRiskProfile() != null && !request.getRiskProfile().isBlank() 
                ? request.getRiskProfile().toUpperCase() 
                : "MODERADO";

        boolean hasAssets = summary.getPortfolios() != null && summary.getPortfolios().stream()
                .anyMatch(p -> p.getAssets() != null && !p.getAssets().isEmpty());

        if (!hasAssets && (summary.getAllocations() == null || summary.getAllocations().isEmpty())) {
            return AiAnalysisResponse.builder()
                    .summary("[" + model + "] Sua carteira está atualmente sem ativos cadastrados.")
                    .riskAssessment("Não é possível avaliar o risco sem investimentos.")
                    .diversificationAdvice(List.of("Cadastre seus ativos manuais na aba Dashboard para iniciar a análise."))
                    .recommendedActions(List.of("Adicionar ações, fundos imobiliários, renda fixa ou opções à sua carteira."))
                    .marketOutlook("O mercado brasileiro exige diversificação entre Renda Fixa (devido à taxa SELIC) e Renda Variável.")
                    .provider(provider)
                    .model(model)
                    .build();
        }

        String portfolioContext = buildPortfolioContext(summary, profile);
        String effectiveKey = resolveEffectiveApiKey(request.getApiKey());

        String prompt = "Você é um consultor sênior de investimentos e analista da B3 credenciado pelo Fred Invest Agent.\n"
                + "Analise a carteira de investimentos real detalhada abaixo considerando o Perfil do Investidor: " + profile + ".\n\n"
                + "=== DADOS COMPLETOS DA CARTEIRA ===\n"
                + portfolioContext + "\n"
                + "=== INSTRUÇÕES OBRIGATÓRIAS DE ANÁLISE ===\n"
                + "Forneça uma análise aprofundada com recomendações REAIS e ESPECÍFICAS de manutenção ou alteração da carteira:\n"
                + "1. DIAGNÓSTICO GERAL E ALINHAMENTO COM O PERFIL (" + profile + "):\n"
                + "   - Avalie o risco e a adequação das alocações ao perfil selecionado (" + profile + ").\n"
                + "2. RECOMENDAÇÕES ATIVO POR ATIVO (MANUTENÇÃO OU ALTERAÇÃO):\n"
                + "   - Para CADA ativo em carteira (ações, FIIs, renda fixa e derivativos/opções), analise os preços médios, cotação atual e lucros/prejuízos.\n"
                + "   - No caso de OPÇÕES: analise o vencimento, strike e cotação da ação base. Diga expressamente se o investidor deve MANTER, EXERCER, ZERAR O POZINHO antes do vencimento para evitar perda total, ou REALIZAR LUCRO.\n"
                + "   - Para ações e FIIs: indique claramente se deve MANTER, AUMENTAR POSIÇÃO (aporte com bom preço médio), REBALANCEAR ou REDUZIR.\n"
                + "3. GESTÃO DE RISCO E DIVERSIFICAÇÃO:\n"
                + "   - Recomendações sobre exposição por classe (renda fixa, reserva, fundos imobiliários, renda variável).\n"
                + "4. PLANO DE AÇÃO IMEDIATO:\n"
                + "   - Liste os passos prioritários que o investidor deve executar agora.\n\n"
                + "Seja direto, técnico e utilize expressamente os tickers e números reais da carteira informada.";

        String liveResponse = null;
        if ("GEMINI".equalsIgnoreCase(provider)) {
            liveResponse = callLiveGoogleGemini(prompt, effectiveKey);
        } else if ("OPENAI".equalsIgnoreCase(provider)) {
            liveResponse = callLiveOpenAi(prompt, effectiveKey);
        } else if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            liveResponse = callLiveDeepSeek(prompt, effectiveKey);
        }

        boolean isLiveOk = liveResponse != null && !liveResponse.startsWith("⚠️") && !liveResponse.startsWith("❌");

        String summaryText;
        String riskAssessment;
        List<String> advice = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        String marketOutlook;

        if (isLiveOk) {
            summaryText = liveResponse;
            riskAssessment = String.format("Análise ao vivo gerada pelo %s com base no perfil %s e nas posições reais da carteira.", model, profile);
            marketOutlook = "Cenário macroeconômico atual com foco em taxa de juros (Selic), inflação (IPCA), controle de risco em derivativos e assimetria positiva de retorno.";

            List<String> parsedActions = extractBulletPoints(liveResponse, "RECOMENDAÇÕES");
            if (parsedActions.isEmpty()) {
                parsedActions = extractBulletPoints(liveResponse, "PLANO DE AÇÃO");
            }
            if (!parsedActions.isEmpty()) {
                actions.addAll(parsedActions);
            }

            List<String> parsedAdvice = extractBulletPoints(liveResponse, "DIVERSIFICAÇÃO");
            if (!parsedAdvice.isEmpty()) {
                advice.addAll(parsedAdvice);
            }
        } else {
            summaryText = liveResponse != null ? liveResponse : String.format("[%s] Resumo para Perfil %s:\n%s", model, profile, portfolioContext);
            riskAssessment = String.format("Perfil de investidor configurado como %s. %s", profile,
                    profile.equalsIgnoreCase("CONSERVADOR") ? "Priorize preservação de capital e liquidez." :
                    profile.equalsIgnoreCase("ARROJADO") ? "Perfil focado em valorização e assimetria, aceitando maior volatilidade." :
                    "Perfil equilibrado entre rentabilidade e segurança.");
            marketOutlook = "O cenário macroeconômico atual favorece estratégias híbridas com proteção em juros reais (IPCA+) e alocação seletiva em empresas pagadoras de dividendos.";
        }

        populateHeuristicAdviceAndActions(summary, profile, advice, actions);

        return AiAnalysisResponse.builder()
                .summary(summaryText)
                .riskAssessment(riskAssessment)
                .diversificationAdvice(advice)
                .recommendedActions(actions)
                .marketOutlook(marketOutlook)
                .provider(provider)
                .model(model)
                .build();
    }

    public AiChatResponse chat(AiChatRequest request, String userEmail) {
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userEmail);
        String[] resolved = resolveProviderAndModel(request.getProvider(), userEmail);
        String provider = resolved[0];
        String model = resolved[1];

        String userQuery = request.getPrompt();
        String profile = request.getRiskProfile() != null && !request.getRiskProfile().isBlank()
                ? request.getRiskProfile().toUpperCase()
                : "MODERADO";
        String effectiveKey = resolveEffectiveApiKey(request.getApiKey());

        String portfolioContext = buildPortfolioContext(summary, profile);
        String enrichedPrompt = "Você é o assistente especialista e consultor de investimentos Fred Invest Agent.\n\n"
                + "=== DADOS DA CARTEIRA DO USUÁRIO ===\n"
                + portfolioContext + "\n"
                + "=== PERGUNTA OU COMANDO DO USUÁRIO ===\n"
                + userQuery + "\n\n"
                + "Responda em português de forma direta, técnica e personalizada, levando em conta o perfil (" + profile + ") e os dados reais de ativos, quantidades, preços médios e derivativos da carteira acima sempre que relevante.";

        String reply = null;
        if ("GEMINI".equalsIgnoreCase(provider)) {
            reply = callLiveGoogleGemini(enrichedPrompt, effectiveKey);
        } else if ("OPENAI".equalsIgnoreCase(provider)) {
            reply = callLiveOpenAi(enrichedPrompt, effectiveKey);
        } else if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            reply = callLiveDeepSeek(enrichedPrompt, effectiveKey);
        } else {
            reply = generateGenericFinancialReply(provider, model, userQuery, summary, profile);
        }

        return AiChatResponse.builder()
                .reply(reply)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .provider(provider)
                .model(model)
                .build();
    }

    private String buildPortfolioContext(PortfolioSummaryDTO summary, String profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Perfil de Risco Escolhido: ").append(profile != null ? profile : "MODERADO").append("\n");
        sb.append(String.format("Patrimônio Total Atual: R$ %,.2f | Total Investido: R$ %,.2f | Lucro/Prejuízo Total: R$ %,.2f (%,.2f%%)\n\n",
                summary.getTotalPatrimony() != null ? summary.getTotalPatrimony() : BigDecimal.ZERO,
                summary.getTotalInvested() != null ? summary.getTotalInvested() : BigDecimal.ZERO,
                summary.getTotalGainLoss() != null ? summary.getTotalGainLoss() : BigDecimal.ZERO,
                summary.getGainLossPercentage() != null ? summary.getGainLossPercentage() : BigDecimal.ZERO));

        sb.append("Alocação Atual por Classes:\n");
        if (summary.getAllocations() != null && !summary.getAllocations().isEmpty()) {
            for (CategoryAllocationDTO alloc : summary.getAllocations()) {
                sb.append(String.format("- %s: R$ %,.2f (%,.1f%%)\n",
                        alloc.getCategory(),
                        alloc.getTotalValue() != null ? alloc.getTotalValue() : BigDecimal.ZERO,
                        alloc.getPercentage() != null ? alloc.getPercentage() : BigDecimal.ZERO));
            }
        } else {
            sb.append("- Nenhuma alocação registrada\n");
        }

        sb.append("\nAtivos Cadastrados na Carteira:\n");
        boolean hasAssets = false;
        if (summary.getPortfolios() != null) {
            for (PortfolioResponse p : summary.getPortfolios()) {
                if (p.getAssets() != null && !p.getAssets().isEmpty()) {
                    hasAssets = true;
                    for (AssetDTO a : p.getAssets()) {
                        sb.append(String.format("- %s (%s): Qtd: %s, Preço Médio: R$ %,.2f, Preço Atual: R$ %,.2f, Valor Total: R$ %,.2f, Resultado: R$ %,.2f (%,.2f%%)",
                                a.getTicker(),
                                a.getCategory(),
                                a.getQuantity() != null ? a.getQuantity().stripTrailingZeros().toPlainString() : "0",
                                a.getAveragePrice() != null ? a.getAveragePrice() : BigDecimal.ZERO,
                                a.getCurrentPrice() != null ? a.getCurrentPrice() : BigDecimal.ZERO,
                                a.getTotalValue() != null ? a.getTotalValue() : BigDecimal.ZERO,
                                a.getGainLoss() != null ? a.getGainLoss() : BigDecimal.ZERO,
                                a.getGainLossPercentage() != null ? a.getGainLossPercentage() : BigDecimal.ZERO));

                        if ("OPCOES".equalsIgnoreCase(a.getCategory() != null ? a.getCategory().name() : "")) {
                            sb.append(" [DERIVATIVO/OPÇÃO: ");
                            if (a.getStrikePrice() != null) {
                                sb.append(String.format("Strike R$ %,.2f; ", a.getStrikePrice()));
                            }
                            if (a.getExpirationDate() != null) {
                                sb.append("Vencimento: ").append(a.getExpirationDate()).append("; ");
                            }
                            if (a.getUnderlyingTicker() != null && !a.getUnderlyingTicker().isBlank()) {
                                sb.append("Ação Base: ").append(a.getUnderlyingTicker());
                                if (a.getUnderlyingPrice() != null) {
                                    sb.append(String.format(" (Cotação da Ação Base R$ %,.2f)", a.getUnderlyingPrice()));
                                }
                            }
                            sb.append("]");
                        }
                        sb.append("\n");
                    }
                }
            }
        }
        if (!hasAssets) {
            sb.append("- Nenhum ativo cadastrado na carteira.\n");
        }
        return sb.toString();
    }

    private List<String> extractBulletPoints(String text, String sectionKeyword) {
        List<String> items = new ArrayList<>();
        if (text == null || text.isBlank()) return items;

        String[] lines = text.split("\n");
        boolean inSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.toUpperCase().contains(sectionKeyword.toUpperCase())) {
                inSection = true;
                continue;
            }

            if (inSection) {
                if (trimmed.startsWith("#") || (trimmed.matches("^[0-9]+\\..*") && !trimmed.startsWith("-") && items.size() >= 2)) {
                    break;
                }

                if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
                    String clean = trimmed.substring(2).trim().replaceAll("^\\*\\*|\\*\\*$", "");
                    if (!clean.isBlank()) items.add(clean);
                } else if (trimmed.matches("^[0-9]+[\\.\\)]\\s+.*")) {
                    String clean = trimmed.replaceFirst("^[0-9]+[\\.\\)]\\s*", "").trim().replaceAll("^\\*\\*|\\*\\*$", "");
                    if (!clean.isBlank()) items.add(clean);
                }
            }
            if (items.size() >= 6) break;
        }
        return items;
    }

    private void populateHeuristicAdviceAndActions(PortfolioSummaryDTO summary, String profile, List<String> advice, List<String> actions) {
        if (actions.isEmpty()) {
            if (summary.getPortfolios() != null) {
                for (PortfolioResponse p : summary.getPortfolios()) {
                    if (p.getAssets() != null) {
                        for (AssetDTO a : p.getAssets()) {
                            if ("OPCOES".equalsIgnoreCase(a.getCategory() != null ? a.getCategory().name() : "")) {
                                actions.add(String.format("Opção %s: Monitorar vencimento em %s (Strike R$ %,.2f). Se estiver fora do dinheiro (OTM), avaliar zerar a posição para evitar virar pó.",
                                        a.getTicker(),
                                        a.getExpirationDate() != null ? a.getExpirationDate().toString() : "breve",
                                        a.getStrikePrice() != null ? a.getStrikePrice() : BigDecimal.ZERO));
                            } else if (a.getGainLossPercentage() != null && a.getGainLossPercentage().compareTo(new BigDecimal("-15")) < 0) {
                                actions.add(String.format("Ativo %s: Desvalorização de %,.1f%% em relação ao preço médio (R$ %,.2f). Reavaliar fundamentos antes de aportar.",
                                        a.getTicker(), a.getGainLossPercentage(), a.getAveragePrice()));
                            } else if (a.getGainLossPercentage() != null && a.getGainLossPercentage().compareTo(new BigDecimal("20")) > 0) {
                                actions.add(String.format("Ativo %s: Lucro de +%,.1f%%. Avaliar realização parcial ou rebalanceamento para novas oportunidades.",
                                        a.getTicker(), a.getGainLossPercentage()));
                            } else {
                                actions.add(String.format("Ativo %s: Manter posição com acompanhamento periódico de resultados.", a.getTicker()));
                            }
                        }
                    }
                }
            }
            if (actions.isEmpty()) {
                actions.add("Realizar aportes regulares com foco em diversificação e reequilíbrio.");
            }
        }

        if (advice.isEmpty()) {
            boolean hasRendaFixa = summary.getAllocations() != null && summary.getAllocations().stream().anyMatch(a -> "RENDA_FIXA".equalsIgnoreCase(a.getCategory() != null ? a.getCategory().name() : ""));
            boolean hasAcoes = summary.getAllocations() != null && summary.getAllocations().stream().anyMatch(a -> "ACOES".equalsIgnoreCase(a.getCategory() != null ? a.getCategory().name() : ""));
            boolean hasFiis = summary.getAllocations() != null && summary.getAllocations().stream().anyMatch(a -> "FIIS".equalsIgnoreCase(a.getCategory() != null ? a.getCategory().name() : ""));
            boolean hasOpcoes = summary.getAllocations() != null && summary.getAllocations().stream().anyMatch(a -> "OPCOES".equalsIgnoreCase(a.getCategory() != null ? a.getCategory().name() : ""));

            if (!hasRendaFixa) {
                advice.add("Recomendado alocar uma parcela em Renda Fixa (Tesouro Selic/CDB) para reserva de liquidez e segurança.");
            }
            if (!hasFiis) {
                advice.add("Fundos Imobiliários (FIIs) proporcionam geração de renda passiva mensal isenta de IR.");
            }
            if (hasOpcoes && "CONSERVADOR".equalsIgnoreCase(profile)) {
                advice.add("Alerta de Risco: Carteira conservadora possui exposição a opções (derivativos). Recomenda-se eliminar para evitar volatilidade extrema.");
            }
            if (!hasAcoes && "ARROJADO".equalsIgnoreCase(profile)) {
                advice.add("Para o perfil Arrojado, a ausência de ações reduz o potencial de valorização no longo prazo.");
            }
            if (advice.isEmpty()) {
                advice.add("Sua carteira apresenta boa distribuição entre as classes de ativos para o perfil " + profile + ".");
            }
        }
    }

    private String resolveEffectiveApiKey(String requestKey) {
        if (requestKey != null && !requestKey.isBlank()) {
            return requestKey.trim();
        }
        String envGemini = System.getenv("GEMINI_API_KEY");
        if (envGemini != null && !envGemini.isBlank()) return envGemini.trim();

        String envOpenAi = System.getenv("OPENAI_API_KEY");
        if (envOpenAi != null && !envOpenAi.isBlank()) return envOpenAi.trim();

        String envAiKey = System.getenv("AI_PROVIDER_API_KEY");
        if (envAiKey != null && !envAiKey.isBlank()) return envAiKey.trim();

        return aiApiKey;
    }

    private String callLiveGoogleGemini(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equalsIgnoreCase("mock-key")) {
            return "⚠️ [Google Gemini API] Nenhuma chave de API (Gemini API Key) foi fornecida. Cole sua chave no campo '🔑 Gemini API Key' no topo da tela para receber respostas ao vivo da internet do Google Gemini.";
        }

        String trimmedKey = apiKey.trim();

        // 1. Busca dinamicamente os modelos disponíveis para esta chave de API específica
        List<String> dynamicEndpoints = fetchAvailableGeminiModels(trimmedKey);

        List<String> candidateEndpoints = new ArrayList<>();
        // Prioriza os melhores modelos encontrados
        for (String ep : dynamicEndpoints) {
            if (ep.contains("2.5-flash") || ep.contains("2.0-flash") || ep.contains("1.5-flash") || ep.contains("1.5-pro")) {
                candidateEndpoints.add(ep);
            }
        }
        for (String ep : dynamicEndpoints) {
            if (!candidateEndpoints.contains(ep)) {
                candidateEndpoints.add(ep);
            }
        }

        // Se a listagem não retornou nenhum modelo, tenta os endpoints estáveis v1 e v1beta
        if (candidateEndpoints.isEmpty()) {
            candidateEndpoints.addAll(List.of(
                "v1/gemini-1.5-flash",
                "v1/gemini-2.0-flash",
                "v1/gemini-1.5-pro",
                "v1beta/gemini-2.0-flash",
                "v1beta/gemini-1.5-flash",
                "v1beta/gemini-2.5-flash"
            ));
        }

        String lastErrorMsg = null;
        int lastStatusCode = 0;

        for (String endpoint : candidateEndpoints) {
            try {
                String apiVersion = endpoint.startsWith("v1beta/") ? "v1beta" : "v1";
                String modelName = endpoint.substring(apiVersion.length() + 1);

                String url = "https://generativelanguage.googleapis.com/" + apiVersion + "/models/" + modelName + ":generateContent?key=" + trimmedKey;

                ObjectNode rootNode = objectMapper.createObjectNode();
                ArrayNode contentsArray = rootNode.putArray("contents");
                ObjectNode contentObj = contentsArray.addObject();
                ArrayNode partsArray = contentObj.putArray("parts");
                ObjectNode partObj = partsArray.addObject();
                partObj.put("text", prompt);

                String payload = objectMapper.writeValueAsString(rootNode);

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", trimmedKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && candidates.size() > 0) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (parts.isArray() && parts.size() > 0) {
                            return "🌐 [Resposta ao Vivo via Google Gemini (" + modelName + " - " + apiVersion + ")]\n\n" + parts.get(0).path("text").asText();
                        }
                    }
                    return resp.body();
                } else {
                    lastStatusCode = resp.statusCode();
                    try {
                        JsonNode root = objectMapper.readTree(resp.body());
                        lastErrorMsg = root.path("error").path("message").asText(resp.body());
                    } catch (Exception ex) {
                        lastErrorMsg = resp.body();
                    }
                    if (resp.statusCode() == 404) {
                        continue;
                    }
                    break;
                }
            } catch (Exception e) {
                return "❌ Exceção ao conectar com a API do Google Gemini: " + e.getMessage();
            }
        }

        return "❌ Erro ao chamar a API do Google Gemini (HTTP " + lastStatusCode + "): " + lastErrorMsg + "\n\n👉 Verifique se a chave inserida no campo '🔑 Gemini API Key' no topo do site está correta e com a API habilitada no Google AI Studio.";
    }

    private List<String> fetchAvailableGeminiModels(String apiKey) {
        List<String> list = new ArrayList<>();
        String[] versions = new String[]{"v1", "v1beta"};
        for (String ver : versions) {
            try {
                String url = "https://generativelanguage.googleapis.com/" + ver + "/models?key=" + apiKey;
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    JsonNode modelsNode = root.path("models");
                    if (modelsNode.isArray() && modelsNode.size() > 0) {
                        for (JsonNode m : modelsNode) {
                            JsonNode methods = m.path("supportedGenerationMethods");
                            boolean supportsGenerate = false;
                            if (methods.isArray()) {
                                for (JsonNode method : methods) {
                                    if ("generateContent".equalsIgnoreCase(method.asText())) {
                                        supportsGenerate = true;
                                        break;
                                    }
                                }
                            }
                            if (supportsGenerate) {
                                String name = m.path("name").asText();
                                if (name.startsWith("models/")) {
                                    name = name.substring("models/".length());
                                }
                                list.add(ver + "/" + name);
                            }
                        }
                    }
                    if (!list.isEmpty()) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private String callLiveOpenAi(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equalsIgnoreCase("mock-key")) {
            return "⚠️ [OpenAI API] Nenhuma chave de API (OpenAI API Key) foi fornecida. Cole sua chave no campo no topo da tela para receber respostas ao vivo da OpenAI.";
        }
        try {
            String url = "https://api.openai.com/v1/chat/completions";

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", "gpt-4o-mini");
            ArrayNode messagesArray = rootNode.putArray("messages");

            ObjectNode sysMsg = messagesArray.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "Você é um assistente financeiro especialista em investimentos.");

            ObjectNode userMsg = messagesArray.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            String payload = objectMapper.writeValueAsString(rootNode);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    return "🌐 [Resposta ao Vivo via OpenAI API (GPT-4o-mini)]\n\n" + choices.get(0).path("message").path("content").asText();
                }
                return resp.body();
            } else {
                JsonNode root = objectMapper.readTree(resp.body());
                String errorMsg = root.path("error").path("message").asText(resp.body());
                return "❌ Erro ao chamar a API da OpenAI (HTTP " + resp.statusCode() + "): " + errorMsg;
            }
        } catch (Exception e) {
            return "❌ Exceção ao conectar com a API da OpenAI: " + e.getMessage();
        }
    }

    private String callLiveDeepSeek(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equalsIgnoreCase("mock-key")) {
            return "⚠️ [DeepSeek API] Nenhuma chave de API (DeepSeek API Key) foi fornecida. Cole sua chave no campo no topo da tela para receber respostas ao vivo da DeepSeek.";
        }
        try {
            String url = "https://api.deepseek.com/chat/completions";

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", "deepseek-chat");
            ArrayNode messagesArray = rootNode.putArray("messages");

            ObjectNode sysMsg = messagesArray.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "Você é um assistente financeiro especialista em investimentos.");

            ObjectNode userMsg = messagesArray.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            String payload = objectMapper.writeValueAsString(rootNode);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    return "🌐 [Resposta ao Vivo via DeepSeek V3]\n\n" + choices.get(0).path("message").path("content").asText();
                }
                return resp.body();
            } else {
                JsonNode root = objectMapper.readTree(resp.body());
                String errorMsg = root.path("error").path("message").asText(resp.body());
                return "❌ Erro ao chamar a API da DeepSeek (HTTP " + resp.statusCode() + "): " + errorMsg;
            }
        } catch (Exception e) {
            return "❌ Exceção ao conectar com a API da DeepSeek: " + e.getMessage();
        }
    }

    private String generateGenericFinancialReply(String provider, String model, String query, PortfolioSummaryDTO summary, String profile) {
        return String.format("[%s - Perfil %s] Analisando sua pergunta '%s' com base na sua carteira: recomendo manter disciplina de aportes, monitorar o vencimento e strike de derivativos e rebalancear as posições periodicamente de acordo com seus objetivos.",
                model, profile, query);
    }
}
