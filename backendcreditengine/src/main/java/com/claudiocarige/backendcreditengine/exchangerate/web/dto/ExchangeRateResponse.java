package com.claudiocarige.backendcreditengine.exchangerate.web.dto;

import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record ExchangeRateResponse(
    UUID id,
    CurrencyCode currency,
    String rateBrl,
    Instant effectiveAt,
    Instant createdAt
) {
    public static ExchangeRateResponse fromDomain(ExchangeRate entity) {
        return new ExchangeRateResponse(
            entity.getId(),
            entity.getCurrency(),
            entity.getRateBrl().setScale(6, RoundingMode.UNNECESSARY).toPlainString(),
            entity.getEffectiveAt(),
            entity.getCreatedAt()
        );
    }
}

