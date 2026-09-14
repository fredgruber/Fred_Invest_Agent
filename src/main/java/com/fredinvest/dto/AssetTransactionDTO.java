package com.fredinvest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetTransactionDTO(
    Long id,
    Long assetId,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal totalValue,
    LocalDateTime transactionDate
) {}

