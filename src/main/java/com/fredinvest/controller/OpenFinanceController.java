package com.fredinvest.controller;

import com.fredinvest.dto.OpenFinanceDTOs.*;
import com.fredinvest.service.OpenFinanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/open-finance")
public class OpenFinanceController {

    private final OpenFinanceService openFinanceService;

    public OpenFinanceController(OpenFinanceService openFinanceService) {
        this.openFinanceService = openFinanceService;
    }

    @GetMapping("/institutions")
    public ResponseEntity<List<InstitutionDTO>> getInstitutions() {
        return ResponseEntity.ok(openFinanceService.getAvailableInstitutions());
    }

    @GetMapping("/xp/config")
    public ResponseEntity<XpConfigDTO> getXpConfig() {
        return ResponseEntity.ok(openFinanceService.getXpConfig());
    }

    @PostMapping("/xp/config")
    public ResponseEntity<XpConfigDTO> updateXpConfig(@RequestBody XpConfigDTO config) {
        return ResponseEntity.ok(openFinanceService.updateXpConfig(config));
    }

    @PostMapping("/xp/consent")
    public ResponseEntity<XpAuthUrlResponseDTO> initiateXpConsent(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.initiateXpConsent(userDetails.getUsername()));
    }

    @PostMapping("/xp/callback")
    public ResponseEntity<SyncResponseDTO> handleXpCallback(@RequestBody XpCallbackRequestDTO callback,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.handleXpCallback(callback, userDetails.getUsername()));
    }

    @GetMapping("/xp/status")
    public ResponseEntity<XpStatusDTO> getXpStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.getXpConnectionStatus(userDetails.getUsername()));
    }

    @PostMapping("/xp/connect")
    public ResponseEntity<SyncResponseDTO> connectXp(@RequestBody XpConnectRequestDTO request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.connectXpAccount(request, userDetails.getUsername()));
    }

    @PostMapping("/xp/sync")
    public ResponseEntity<SyncResponseDTO> syncXp(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.connectXpAccount(new XpConnectRequestDTO(), userDetails.getUsername()));
    }

    @PostMapping("/consent")
    public ResponseEntity<ConsentResponseDTO> requestConsent(@Valid @RequestBody ConsentRequestDTO request,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.requestConsent(request, userDetails.getUsername()));
    }

    @PostMapping("/consent/{consentId}/authorize")
    public ResponseEntity<SyncResponseDTO> authorizeConsent(@PathVariable String consentId,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.authorizeAndSyncConsent(consentId, userDetails.getUsername()));
    }

    @GetMapping("/consents")
    public ResponseEntity<List<ConsentStatusDTO>> getUserConsents(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(openFinanceService.getUserConsents(userDetails.getUsername()));
    }

    @DeleteMapping("/consents/{id}")
    public ResponseEntity<Void> revokeConsent(@PathVariable Long id,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        openFinanceService.revokeConsent(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
