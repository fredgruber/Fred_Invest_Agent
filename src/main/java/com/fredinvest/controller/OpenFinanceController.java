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
}
