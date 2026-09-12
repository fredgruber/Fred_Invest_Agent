package com.fredinvest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

public class AiDTOs {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiAnalysisRequest {
        private Long portfolioId;
        private String riskProfile; // CONSERVADOR, MODERADO, ARROJADO
        private String customGoal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiAnalysisResponse {
        private String summary;
        private String riskAssessment;
        private List<String> diversificationAdvice;
        private List<String> recommendedActions;
        private String marketOutlook;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiChatRequest {
        @NotBlank
        private String prompt;
        private Long portfolioId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiChatResponse {
        private String reply;
        private String timestamp;
    }
}

