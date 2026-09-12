package com.fredinvest.controller;

import com.fredinvest.dto.AiDTOs.*;
import com.fredinvest.service.AiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResponse> analyzePortfolio(@RequestBody AiAnalysisRequest request,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiService.analyzePortfolio(request, userDetails.getUsername()));
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiService.chat(request, userDetails.getUsername()));
    }
}
