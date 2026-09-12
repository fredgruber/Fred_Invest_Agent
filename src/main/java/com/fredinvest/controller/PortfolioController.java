package com.fredinvest.controller;

import com.fredinvest.dto.PortfolioDTOs.*;
import com.fredinvest.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> getUserPortfolios(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.getUserPortfolios(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> createPortfolio(@Valid @RequestBody PortfolioRequest request,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.createPortfolio(request, userDetails.getUsername()));
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryDTO> getPortfolioSummary(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary(userDetails.getUsername()));
    }

    @PostMapping("/{portfolioId}/assets")
    public ResponseEntity<AssetDTO> addOrUpdateAsset(@PathVariable Long portfolioId,
                                                     @Valid @RequestBody AssetDTO assetDTO,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.addOrUpdateAsset(portfolioId, assetDTO, userDetails.getUsername()));
    }

    @DeleteMapping("/{portfolioId}/assets/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long portfolioId,
                                            @PathVariable Long assetId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        portfolioService.deleteAsset(portfolioId, assetId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
