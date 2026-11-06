package com.claudiocarige.backendcreditengine.exchangerate.persistence;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade JPA para registro append-only de cotações cambiais (USD -> BRL).
 */
@Entity
@Table(name = "exchange_rates")
@Immutable
public class ExchangeRate {

    private static final BigDecimal MIN_RATE = new BigDecimal("0.000001");
    private static final BigDecimal MAX_RATE = new BigDecimal("9999999.999999");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private CurrencyCode currency;

    @Column(name = "rate_brl", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal rateBrl;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExchangeRate() {
    }

    public ExchangeRate(CurrencyCode currency, BigDecimal rateBrl, Instant effectiveAt, Instant createdAt) {

        Objects.requireNonNull(currency, "currency cannot be null");

        if (currency != CurrencyCode.USD) {
            throw new IllegalArgumentException("currency must be USD: " + currency);
        }

        this.currency = currency;

        Objects.requireNonNull(rateBrl, "rateBrl cannot be null");
        if (rateBrl.scale() > 6) {
            throw new IllegalArgumentException("rateBrl scale cannot exceed 6 decimal places: " + rateBrl);
        }
        if (rateBrl.compareTo(MIN_RATE) < 0 || rateBrl.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("rateBrl must be between " + MIN_RATE + " and " + MAX_RATE);
        }
        this.rateBrl = rateBrl;

        this.effectiveAt = validateEffectiveAt(effectiveAt);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null").truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public BigDecimal getRateBrl() {
        return rateBrl;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static Instant validateEffectiveAt(Instant effectiveAt) {

        Objects.requireNonNull(
                effectiveAt,
                "effectiveAt cannot be null");

        if (effectiveAt.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException(
                    "effectiveAt must have microsecond precision");
        }

        return effectiveAt;
    }
}
