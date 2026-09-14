package com.fredinvest.repository;

import com.fredinvest.model.AssetTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetTransactionRepository extends JpaRepository<AssetTransaction, Long> {
    List<AssetTransaction> findByAssetIdOrderByTransactionDateDesc(Long assetId);
    void deleteByAssetId(Long assetId);
}

