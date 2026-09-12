package com.fredinvest.repository;

import com.fredinvest.model.OpenFinanceConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpenFinanceConsentRepository extends JpaRepository<OpenFinanceConsent, Long> {
    List<OpenFinanceConsent> findByUserId(Long userId);
    Optional<OpenFinanceConsent> findByConsentId(String consentId);
}

