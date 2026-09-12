package com.fredinvest.repository;

import com.fredinvest.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByPortfolioId(Long portfolioId);
    Optional<Asset> findByPortfolioIdAndTicker(Long portfolioId, String ticker);
}

