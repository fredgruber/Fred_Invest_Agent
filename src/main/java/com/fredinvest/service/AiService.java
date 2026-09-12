package com.fredinvest.service;

import com.fredinvest.dto.AiDTOs.*;
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

        String profile = request.getRiskProfile() != null ? request.getRiskProfile() : "MODERADO";
        BigDecimal totalPatrimony = summary.getTotalPatrimony();

        List<String> advice = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (summary.getAllocations().isEmpty()) {
            return AiAnalysisResponse.builder()
                    .summary("[" + model + "] Sua carteira está atualmente sem ativos cadastrados.")
                    .riskAssessment("Não é possível avaliar o risco sem investimentos.")
                    .diversificationAdvice(List.of("Cadastre seus ativos manuais ou conecte ao Open Finance para iniciar a análise."))
                    .recommendedActions(List.of("Adicionar ações, fundos imobiliários ou renda fixa à sua carteira."))
                    .marketOutlook("O mercado brasileiro exige diversificação entre Renda Fixa (devido à taxa SELIC) e Renda Variável.")
                    .provider(provider)
                    .model(model)
                    .build();
        }

        // Análise de Diversificação
        boolean hasRendaFixa = summary.getAllocations().stream().anyMatch(a -> a.getCategory().name().equals("RENDA_FIXA"));
        boolean hasAcoes = summary.getAllocations().stream().anyMatch(a -> a.getCategory().name().equals("ACOES"));
        boolean hasFiis = summary.getAllocations().stream().anyMatch(a -> a.getCategory().name().equals("FIIS"));

        if (!hasRendaFixa) {
            advice.add("Recomendado alocar uma parcela em Renda Fixa (CDB/Tesouro Direto) para reserva de emergência e proteção contra volatilidade.");
        }
        if (!hasFiis) {
            advice.add("Fundos Imobiliários (FIIs) podem proporcionar uma excelente geração de caixa passiva mensal isenta de IR.");
        }
        if (!hasAcoes && profile.equalsIgnoreCase("ARROJADO")) {
            advice.add("Para o perfil Arrojado, a ausência de ações reduz o potencial de valorização no longo prazo.");
        }

        if (advice.isEmpty()) {
            advice.add("Sua carteira apresenta boa distribuição entre as classes de ativos alinhada ao mercado.");
        }

        // Ações Recomendadas
        actions.add("Reequilibrar aportes priorizando ativos com menor representatividade relativa.");
        actions.add("Monitorar periodicamente o preço médio dos ativos e a distribuição de dividendos.");
        if (summary.getTotalGainLoss().compareTo(BigDecimal.ZERO) < 0) {
            actions.add("Aproveitar momentos de queda para aportar em empresas fundamentadas a bons preços médios.");
        } else {
            actions.add("Manter a disciplina de aportes mensais constantes (DCA - Dollar Cost Averaging).");
        }

        String summaryText = String.format("[%s] Carteira consolidada com patrimônio total de R$ %,.2f e rentabilidade acumulada de %,.2f%%.",
                model, totalPatrimony, summary.getGainLossPercentage());

        String riskText = String.format("Perfil de investidor configurado como %s. A alocação atual possui %d classe(s) de ativos.",
                profile.toUpperCase(), summary.getAllocations().size());

        String marketOutlook = "O cenário macroeconômico atual favorece estratégias híbridas com proteção em juros reais (IPCA+) e alocação seletiva em empresas pagadoras de dividendos.";

        String effectiveKey = resolveEffectiveApiKey(request.getApiKey());
        if ("GEMINI".equalsIgnoreCase(provider)) {
            String liveAnalysis = callLiveGoogleGemini("Faça um resumo executivo resumido de análise para carteira de investimentos com patrimônio total de R$ " + totalPatrimony + ", perfil " + profile + " e alocações: " + summary.getAllocations(), effectiveKey);
            if (liveAnalysis != null && !liveAnalysis.isBlank()) {
                summaryText = "🌐 [Análise ao Vivo via Google Gemini] " + liveAnalysis;
            }
        } else if ("OPENAI".equalsIgnoreCase(provider)) {
            String liveAnalysis = callLiveOpenAi("Faça um resumo executivo resumido de análise para carteira de investimentos com patrimônio total de R$ " + totalPatrimony + ", perfil " + profile + " e alocações: " + summary.getAllocations(), effectiveKey);
            if (liveAnalysis != null && !liveAnalysis.isBlank()) {
                summaryText = "🌐 [Análise ao Vivo via OpenAI] " + liveAnalysis;
            }
        }

        return AiAnalysisResponse.builder()
                .summary(summaryText)
                .riskAssessment(riskText)
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
        String effectiveKey = resolveEffectiveApiKey(request.getApiKey());
        String reply;

        if ("GEMINI".equalsIgnoreCase(provider)) {
            reply = callLiveGoogleGemini(userQuery, effectiveKey);
        } else if ("OPENAI".equalsIgnoreCase(provider)) {
            reply = callLiveOpenAi(userQuery, effectiveKey);
        } else {
            reply = generateGenericFinancialReply(provider, userQuery);
        }

        String formattedReply = String.format("[%s] %s", model, reply);

        return AiChatResponse.builder()
                .reply(formattedReply)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .provider(provider)
                .model(model)
                .build();
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

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String callLiveGoogleGemini(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equalsIgnoreCase("mock-key")) {
            return "⚠️ [Google Gemini API] Nenhuma chave de API (Gemini API Key) foi fornecida. Cole sua chave no campo '🔑 Gemini API Key' no topo da tela para receber respostas ao vivo da internet do Google Gemini.";
        }
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey.trim();
            String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            String payload = "{\"contents\":[{\"parts\":[{\"text\":\"Você é o assistente especialista de investimentos Fred Invest Agent. Responda em português de forma direta e detalhada sobre: " + escapedPrompt + "\"}]}]}";

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                com.fasterxml.jackson.databind.JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    com.fasterxml.jackson.databind.JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        return "🌐 [Resposta ao Vivo via Google Gemini API]\n\n" + parts.get(0).path("text").asText();
                    }
                }
                return resp.body();
            } else {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                String errorMsg = root.path("error").path("message").asText(resp.body());
                return "❌ Erro ao chamar a API do Google Gemini (HTTP " + resp.statusCode() + "): " + errorMsg + "\n\n👉 Verifique se a chave inserida no campo '🔑 Gemini API Key' no topo do site está correta.";
            }
        } catch (Exception e) {
            return "❌ Exceção ao conectar com a API do Google Gemini: " + e.getMessage();
        }
    }

    private String callLiveOpenAi(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || apiKey.equalsIgnoreCase("mock-key")) {
            return "⚠️ [OpenAI API] Nenhuma chave de API (OpenAI API Key) foi fornecida. Cole sua chave no campo de chave no topo da tela para receber respostas ao vivo da OpenAI.";
        }
        try {
            String url = "https://api.openai.com/v1/chat/completions";
            String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            String payload = "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"system\",\"content\":\"Você é um assistente financeiro especialista em investimentos.\"},{\"role\":\"user\",\"content\":\"" + escapedPrompt + "\"}]}";

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                com.fasterxml.jackson.databind.JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    return "🌐 [Resposta ao Vivo via OpenAI API]\n\n" + choices.get(0).path("message").path("content").asText();
                }
                return resp.body();
            } else {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                String errorMsg = root.path("error").path("message").asText(resp.body());
                return "❌ Erro ao chamar a API da OpenAI (HTTP " + resp.statusCode() + "): " + errorMsg;
            }
        } catch (Exception e) {
            return "❌ Exceção ao conectar com a API da OpenAI: " + e.getMessage();
        }
    }
    private String generateGenericFinancialReply(String provider, String model, String query, PortfolioSummaryDTO summary) {
        switch (provider) {
            case "GEMINI":
                return String.format("Com base nas últimas tendências de mercado do Google Gemini para '%s': no cenário macroeconômico atual com Selic elevada, a melhor estratégia é combinar juros reais com ativos descontados em bolsa.", query);
            case "CLAUDE":
                return String.format("Avaliando '%s' sob a ótica de controle de risco: priorize empresas com baixo endividamento líquido/EBITDA e mantenha sua reserva de emergência em liquidez diária.", query);
            case "DEEPSEEK":
                return String.format("Análise quantitativa para '%s': considere indicadores de valuation como P/VP < 1.0 e Dividend Yield acima de 8%% a.a. para maximizar o retorno esperado ajustado ao risco.", query);
            case "OPENAI":
            default:
                return String.format("Analisando sua pergunta '%s': recomendo estruturar uma estratégia de aportes recorrentes (DCA), focando em reequilíbrio periódico da carteira de investimentos.", query);
        }
    }
}
