package com.claudiocarige.backendcreditengine.exchangerate.persistence;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
        CurrencyCode currency,
        Instant effectiveAt
    );

    boolean existsByCurrencyAndEffectiveAt(CurrencyCode currency, Instant effectiveAt);
}

