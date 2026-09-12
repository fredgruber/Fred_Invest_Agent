package com.fredinvest.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class AiDTOs {

    public static class AiAnalysisRequest {
        private Long portfolioId;
        private String riskProfile;
        private String customGoal;

        public AiAnalysisRequest() {}
        public AiAnalysisRequest(Long portfolioId, String riskProfile, String customGoal) {
            this.portfolioId = portfolioId;
            this.riskProfile = riskProfile;
            this.customGoal = customGoal;
        }

        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

        public String getRiskProfile() { return riskProfile; }
        public void setRiskProfile(String riskProfile) { this.riskProfile = riskProfile; }

        public String getCustomGoal() { return customGoal; }
        public void setCustomGoal(String customGoal) { this.customGoal = customGoal; }

        public static AiAnalysisRequestBuilder builder() { return new AiAnalysisRequestBuilder(); }
        public static class AiAnalysisRequestBuilder {
            private Long portfolioId;
            private String riskProfile;
            private String customGoal;
            public AiAnalysisRequestBuilder portfolioId(Long portfolioId) { this.portfolioId = portfolioId; return this; }
            public AiAnalysisRequestBuilder riskProfile(String riskProfile) { this.riskProfile = riskProfile; return this; }
            public AiAnalysisRequestBuilder customGoal(String customGoal) { this.customGoal = customGoal; return this; }
            public AiAnalysisRequest build() { return new AiAnalysisRequest(portfolioId, riskProfile, customGoal); }
        }
    }

    public static class AiAnalysisResponse {
        private String summary;
        private String riskAssessment;
        private List<String> diversificationAdvice;
        private List<String> recommendedActions;
        private String marketOutlook;

        public AiAnalysisResponse() {}
        public AiAnalysisResponse(String summary, String riskAssessment, List<String> diversificationAdvice, List<String> recommendedActions, String marketOutlook) {
            this.summary = summary;
            this.riskAssessment = riskAssessment;
            this.diversificationAdvice = diversificationAdvice;
            this.recommendedActions = recommendedActions;
            this.marketOutlook = marketOutlook;
        }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public String getRiskAssessment() { return riskAssessment; }
        public void setRiskAssessment(String riskAssessment) { this.riskAssessment = riskAssessment; }

        public List<String> getDiversificationAdvice() { return diversificationAdvice; }
        public void setDiversificationAdvice(List<String> diversificationAdvice) { this.diversificationAdvice = diversificationAdvice; }

        public List<String> getRecommendedActions() { return recommendedActions; }
        public void setRecommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; }

        public String getMarketOutlook() { return marketOutlook; }
        public void setMarketOutlook(String marketOutlook) { this.marketOutlook = marketOutlook; }

        public static AiAnalysisResponseBuilder builder() { return new AiAnalysisResponseBuilder(); }
        public static class AiAnalysisResponseBuilder {
            private String summary;
            private String riskAssessment;
            private List<String> diversificationAdvice;
            private List<String> recommendedActions;
            private String marketOutlook;
            public AiAnalysisResponseBuilder summary(String summary) { this.summary = summary; return this; }
            public AiAnalysisResponseBuilder riskAssessment(String riskAssessment) { this.riskAssessment = riskAssessment; return this; }
            public AiAnalysisResponseBuilder diversificationAdvice(List<String> diversificationAdvice) { this.diversificationAdvice = diversificationAdvice; return this; }
            public AiAnalysisResponseBuilder recommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; return this; }
            public AiAnalysisResponseBuilder marketOutlook(String marketOutlook) { this.marketOutlook = marketOutlook; return this; }
            public AiAnalysisResponse build() { return new AiAnalysisResponse(summary, riskAssessment, diversificationAdvice, recommendedActions, marketOutlook); }
        }
    }

    public static class AiChatRequest {
        @NotBlank
        private String prompt;
        private Long portfolioId;

        public AiChatRequest() {}
        public AiChatRequest(String prompt, Long portfolioId) {
            this.prompt = prompt;
            this.portfolioId = portfolioId;
        }

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }

        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

        public static AiChatRequestBuilder builder() { return new AiChatRequestBuilder(); }
        public static class AiChatRequestBuilder {
            private String prompt;
            private Long portfolioId;
            public AiChatRequestBuilder prompt(String prompt) { this.prompt = prompt; return this; }
            public AiChatRequestBuilder portfolioId(Long portfolioId) { this.portfolioId = portfolioId; return this; }
            public AiChatRequest build() { return new AiChatRequest(prompt, portfolioId); }
        }
    }

    public static class AiChatResponse {
        private String reply;
        private String timestamp;

        public AiChatResponse() {}
        public AiChatResponse(String reply, String timestamp) {
            this.reply = reply;
            this.timestamp = timestamp;
        }

        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public static AiChatResponseBuilder builder() { return new AiChatResponseBuilder(); }
        public static class AiChatResponseBuilder {
            private String reply;
            private String timestamp;
            public AiChatResponseBuilder reply(String reply) { this.reply = reply; return this; }
            public AiChatResponseBuilder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
            public AiChatResponse build() { return new AiChatResponse(reply, timestamp); }
        }
    }
}
