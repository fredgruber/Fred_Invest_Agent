package com.fredinvest.service;

import com.fredinvest.dto.AiDTOs.*;
import com.fredinvest.dto.PortfolioDTOs.PortfolioSummaryDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiService {

    private final PortfolioService portfolioService;

    @Value("${app.ai.api-key}")
    private String aiApiKey;

    public AiService(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    public AiAnalysisResponse analyzePortfolio(AiAnalysisRequest request, String userEmail) {
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userEmail);

        String profile = request.getRiskProfile() != null ? request.getRiskProfile() : "MODERADO";
        BigDecimal totalPatrimony = summary.getTotalPatrimony();

        List<String> advice = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (summary.getAllocations().isEmpty()) {
            return AiAnalysisResponse.builder()
                    .summary("Sua carteira está atualmente sem ativos cadastrados.")
                    .riskAssessment("Não é possível avaliar o risco sem investimentos.")
                    .diversificationAdvice(List.of("Cadastre seus ativos manuais ou conecte ao Open Finance para iniciar a análise."))
                    .recommendedActions(List.of("Adicionar ações, fundos imobiliários ou renda fixa à sua carteira."))
                    .marketOutlook("O mercado brasileiro exige diversificação entre Renda Fixa (devido à taxa SELIC) e Renda Variável.")
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

        String summaryText = String.format("Carteira consolidada com patrimônio total de R$ %,.2f e rentabilidade acumulada de %,.2f%%.",
                totalPatrimony, summary.getGainLossPercentage());

        String riskText = String.format("Perfil de investidor configurado como %s. A alocação atual possui %d classe(s) de ativos.",
                profile.toUpperCase(), summary.getAllocations().size());

        String marketOutlook = "O cenário macroeconômico atual favorece estratégias híbridas com proteção em juros reais (IPCA+) e alocação seletiva em empresas pagadoras de dividendos.";

        return AiAnalysisResponse.builder()
                .summary(summaryText)
                .riskAssessment(riskText)
                .diversificationAdvice(advice)
                .recommendedActions(actions)
                .marketOutlook(marketOutlook)
                .build();
    }

    public AiChatResponse chat(AiChatRequest request, String userEmail) {
        PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(userEmail);
        String prompt = request.getPrompt().toLowerCase();

        String reply;
        if (prompt.contains("patrimonio") || prompt.contains("saldo") || prompt.contains("total")) {
            reply = String.format("Seu patrimônio total cadastrado na plataforma é de R$ %,.2f com um lucro/prejuízo acumulado de R$ %,.2f (%,.2f%%).",
                    summary.getTotalPatrimony(), summary.getTotalGainLoss(), summary.getGainLossPercentage());
        } else if (prompt.contains("diversif") || prompt.contains("alocac")) {
            reply = "Sua carteira possui " + summary.getAllocations().size() + " classe(s) de ativos cadastradas. Uma boa regra prática é manter até 20-30% por categoria dependendo do seu perfil de risco.";
        } else if (prompt.contains("open finance") || prompt.contains("banco")) {
            reply = "Com a autorização do Open Finance, conseguimos importar e atualizar seus saldos bancários e investimentos de forma automática e segura sem digitação manual!";
        } else {
            reply = "Analisando sua pergunta: '" + request.getPrompt() + "'. Como seu assistente financeiro de IA, recomendo focar na diversificação de ativos e acompanhamento constante da rentabilidade real acumulada.";
        }

        return AiChatResponse.builder()
                .reply(reply)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }
}
