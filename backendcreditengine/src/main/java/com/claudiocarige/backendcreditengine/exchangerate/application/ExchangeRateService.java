package com.claudiocarige.backendcreditengine.exchangerate.application;

import com.claudiocarige.backendcreditengine.exchangerate.application.exception.DuplicateExchangeRateException;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.ExchangeRateUnavailableException;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRateRepository;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateService {

    private static final String UK_EXCHANGE_RATES_CURRENCY_EFFECTIVE_AT = "uk_exchange_rates_currency_effective_at";

    private final ExchangeRateRepository repository;
    private final Clock clock;

    public ExchangeRateService(ExchangeRateRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Transactional
    public ExchangeRate registerExchangeRate(CurrencyCode currency, BigDecimal rateBrl, Instant effectiveAt) {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(rateBrl, "rateBrl cannot be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");

        ExchangeRate entity = new ExchangeRate(currency, rateBrl, effectiveAt, clock.instant());
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            handleDataIntegrityViolation(ex, currency, effectiveAt);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ExchangeRate getExchangeRate(CurrencyCode currency, Instant operationInstant) {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(operationInstant, "operationInstant cannot be null");

        return repository.findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(currency, operationInstant)
                .orElseThrow(() -> new ExchangeRateUnavailableException(currency, operationInstant));
    }

    private void handleDataIntegrityViolation(DataIntegrityViolationException ex, CurrencyCode currency, Instant effectiveAt) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    if (sqlEx.getMessage() != null && sqlEx.getMessage().contains(UK_EXCHANGE_RATES_CURRENCY_EFFECTIVE_AT)) {
                        throw new DuplicateExchangeRateException(currency, effectiveAt);
                    }
                }
            } else if (cause.getMessage() != null && cause.getMessage().contains(UK_EXCHANGE_RATES_CURRENCY_EFFECTIVE_AT)) {
                throw new DuplicateExchangeRateException(currency, effectiveAt);
            }
            cause = cause.getCause();
        }
        throw ex;
    }
}
