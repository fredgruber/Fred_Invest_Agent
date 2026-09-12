package com.fredinvest.service;

import com.fredinvest.dto.OpenFinanceDTOs.*;
import com.fredinvest.dto.PortfolioDTOs.AssetDTO;
import com.fredinvest.dto.PortfolioDTOs.PortfolioResponse;
import com.fredinvest.model.AssetCategory;
import com.fredinvest.model.OpenFinanceConsent;
import com.fredinvest.model.User;
import com.fredinvest.repository.OpenFinanceConsentRepository;
import com.fredinvest.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OpenFinanceService {

    private final OpenFinanceConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final PortfolioService portfolioService;

    public OpenFinanceService(OpenFinanceConsentRepository consentRepository, UserRepository userRepository, PortfolioService portfolioService) {
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
        this.portfolioService = portfolioService;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));
    }

    public List<InstitutionDTO> getAvailableInstitutions() {
        return List.of(
                InstitutionDTO.builder().id("itau").name("Itaú Unibanco").logoUrl("🏛️").primaryColor("#EC7000").build(),
                InstitutionDTO.builder().id("bradesco").name("Banco Bradesco").logoUrl("🏦").primaryColor("#CC092F").build(),
                InstitutionDTO.builder().id("bb").name("Banco do Brasil").logoUrl("🟡").primaryColor("#F8D117").build(),
                InstitutionDTO.builder().id("nubank").name("Nubank / NuInvest").logoUrl("💜").primaryColor("#8A05BE").build(),
                InstitutionDTO.builder().id("xp").name("XP Investimentos").logoUrl("📈").primaryColor("#000000").build(),
                InstitutionDTO.builder().id("btg").name("BTG Pactual").logoUrl("🌐").primaryColor("#0A2540").build()
        );
    }

    @SuppressWarnings("null")
	@Transactional
    public ConsentResponseDTO requestConsent(ConsentRequestDTO request, String userEmail) {
        User user = getUserByEmail(userEmail);

        InstitutionDTO institution = getAvailableInstitutions().stream()
                .filter(i -> i.getId().equalsIgnoreCase(request.getInstitutionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Instituição financeira não suportada."));

        String consentId = "urn:fredinvest:consent:" + UUID.randomUUID().toString();

        OpenFinanceConsent consent = OpenFinanceConsent.builder()
                .user(user)
                .institutionId(institution.getId())
                .institutionName(institution.getName())
                .consentId(consentId)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusYears(1))
                .build();

        consentRepository.save(consent);

        String redirectUrl = "/open-finance-auth.html?consent_id=" + consentId + "&institution=" + institution.getId();

        return ConsentResponseDTO.builder()
                .consentId(consentId)
                .redirectUrl(redirectUrl)
                .status("PENDING")
                .institutionName(institution.getName())
                .build();
    }

    @SuppressWarnings("null")
	@Transactional
    public SyncResponseDTO authorizeAndSyncConsent(String consentId, String userEmail) {
        User user = getUserByEmail(userEmail);

        OpenFinanceConsent consent = consentRepository.findByConsentId(consentId)
                .orElseThrow(() -> new IllegalArgumentException("Consentimento não encontrado: " + consentId));

        if (!consent.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Acesso negado a este consentimento.");
        }

        consent.setStatus("AUTHORIZED");
        consent.setAccessToken("token_openfinance_" + UUID.randomUUID().toString());
        consentRepository.save(consent);

        // Obter carteira principal do usuário
        List<PortfolioResponse> portfolios = portfolioService.getUserPortfolios(userEmail);
        Long portfolioId = portfolios.get(0).getId();

        // Importar ativos simulados via Open Finance Brasil para a carteira
        int importedCount = 0;
        if ("xp".equalsIgnoreCase(consent.getInstitutionId()) || "btg".equalsIgnoreCase(consent.getInstitutionId())) {
            portfolioService.addOrUpdateAsset(portfolioId, AssetDTO.builder()
                    .ticker("PETR4")
                    .name("Petróleo Brasileiro S.A.")
                    .category(AssetCategory.ACOES)
                    .quantity(new BigDecimal("100"))
                    .averagePrice(new BigDecimal("35.50"))
                    .currentPrice(new BigDecimal("38.20"))
                    .build(), userEmail);

            portfolioService.addOrUpdateAsset(portfolioId, AssetDTO.builder()
                    .ticker("HGLG11")
                    .name("CSHG Logística FII")
                    .category(AssetCategory.FIIS)
                    .quantity(new BigDecimal("50"))
                    .averagePrice(new BigDecimal("155.00"))
                    .currentPrice(new BigDecimal("162.30"))
                    .build(), userEmail);
            importedCount = 2;
        } else {
            portfolioService.addOrUpdateAsset(portfolioId, AssetDTO.builder()
                    .ticker("CDB-SELIC")
                    .name("CDB Liquidez Diária 100% CDI")
                    .category(AssetCategory.RENDA_FIXA)
                    .quantity(new BigDecimal("1"))
                    .averagePrice(new BigDecimal("5000.00"))
                    .currentPrice(new BigDecimal("5120.50"))
                    .build(), userEmail);
            importedCount = 1;
        }

        return SyncResponseDTO.builder()
                .status("SUCCESS")
                .importedAssetsCount(importedCount)
                .message("Consentimento autorizado! " + importedCount + " ativo(s) sincronizado(s) da instituição " + consent.getInstitutionName())
                .syncedAt(LocalDateTime.now())
                .build();
    }

    public List<ConsentStatusDTO> getUserConsents(String userEmail) {
        User user = getUserByEmail(userEmail);

        return consentRepository.findByUserId(user.getId()).stream()
                .map(c -> ConsentStatusDTO.builder()
                        .id(c.getId())
                        .consentId(c.getConsentId())
                        .institutionId(c.getInstitutionId())
                        .institutionName(c.getInstitutionName())
                        .status(c.getStatus())
                        .expiresAt(c.getExpiresAt())
                        .build())
                .collect(Collectors.toList());
    }
}
