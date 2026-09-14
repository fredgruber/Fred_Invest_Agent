package com.fredinvest.controller;

import com.fredinvest.dto.PortfolioDTOs.*;
import com.fredinvest.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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

    @GetMapping("/quote/{ticker}")
    public ResponseEntity<java.util.Map<String, Object>> getLiveQuote(@PathVariable String ticker,
                                                                      @RequestParam(required = false) com.fredinvest.model.AssetCategory category) {
        com.fredinvest.service.PortfolioService.LiveQuote quote = portfolioService.fetchLiveQuote(ticker, category);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("ticker", ticker.toUpperCase().trim());
        if (quote != null) {
            result.put("price", quote.getPrice());
            result.put("source", quote.getSource());
            result.put("name", quote.getName());
            result.put("category", quote.getCategory() != null ? quote.getCategory().name() : null);
            result.put("strikePrice", quote.getStrikePrice());
            result.put("expirationDate", quote.getExpirationDate() != null ? quote.getExpirationDate().toString() : null);
            result.put("underlyingTicker", quote.getUnderlyingTicker());
            result.put("underlyingPrice", quote.getUnderlyingPrice());
            result.put("found", true);
        } else {
            result.put("price", null);
            result.put("found", false);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{portfolioId}/assets/{assetId}/history")
    public ResponseEntity<java.util.List<com.fredinvest.dto.AssetTransactionDTO>> getAssetPurchaseHistory(
            @PathVariable @NonNull Long portfolioId,
            @PathVariable @NonNull Long assetId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.getAssetPurchaseHistory(portfolioId, assetId, userDetails.getUsername()));
    }

    @DeleteMapping("/{portfolioId}/assets/{assetId}/history/{transactionId}")
    public ResponseEntity<java.util.Map<String, Object>> deleteAssetTransaction(
            @PathVariable @NonNull Long portfolioId,
            @PathVariable @NonNull Long assetId,
            @PathVariable @NonNull Long transactionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean assetDeleted = portfolioService.deleteAssetTransaction(portfolioId, assetId, transactionId, userDetails.getUsername());
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("assetDeleted", assetDeleted);
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{portfolioId}/assets/{assetId}/history/{transactionId}")
    public ResponseEntity<java.util.List<com.fredinvest.dto.AssetTransactionDTO>> updateAssetTransactionQuantity(
            @PathVariable @NonNull Long portfolioId,
            @PathVariable @NonNull Long assetId,
            @PathVariable @NonNull Long transactionId,
            @RequestParam @NonNull java.math.BigDecimal quantity,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.updateAssetTransactionQuantity(portfolioId, assetId, transactionId, quantity, userDetails.getUsername()));
    }

    @PostMapping("/{portfolioId}/assets")
    public ResponseEntity<AssetDTO> addOrUpdateAsset(@PathVariable @NonNull Long portfolioId,
                                                     @Valid @RequestBody AssetDTO assetDTO,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.addOrUpdateAsset(portfolioId, assetDTO, userDetails.getUsername()));
    }

    @PutMapping("/{portfolioId}/assets/{assetId}/price")
    public ResponseEntity<AssetDTO> updateAssetPrice(@PathVariable Long portfolioId,
                                                     @PathVariable @NonNull Long assetId,
                                                     @RequestParam java.math.BigDecimal price,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.updateAssetPrice(portfolioId, assetId, price, userDetails.getUsername()));
    }

    @DeleteMapping("/{portfolioId}/assets/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable @NonNull Long portfolioId,
                                            @PathVariable @NonNull Long assetId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        portfolioService.deleteAsset(portfolioId, assetId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
