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
import java.time.ZoneOffset;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Mock
    private ExchangeRateRepository repository;

    private Clock fixedClock;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ExchangeRateService(repository, fixedClock);
    }

    @Test
    @DisplayName("Should successfully register exchange rate and return persisted entity")
    void shouldRegisterExchangeRateSuccessfully() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);
        ExchangeRate expected = new ExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt, NOW);

        when(repository.saveAndFlush(any(ExchangeRate.class))).thenReturn(expected);

        ExchangeRate result = service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt);

        assertThat(result).isNotNull();
        assertThat(result.getCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(result.getRateBrl()).isEqualTo(rateBrl);
        assertThat(result.getEffectiveAt()).isEqualTo(effectiveAt);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        verify(repository).saveAndFlush(any(ExchangeRate.class));
    }

    @Test
    @DisplayName("Should translate Hibernate ConstraintViolationException with SQLState 23505 and matching constraint into DuplicateExchangeRateException")
    void shouldTranslateHibernateConstraintViolationExceptionWithSqlState23505() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);

        SQLException sqlEx = new SQLException("duplicate", "23505");
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                sqlEx,
                "uk_exchange_rates_currency_effective_at"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("data integrity", cve);

        when(repository.saveAndFlush(any(ExchangeRate.class))).thenThrow(dive);

        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt))
                .isInstanceOf(DuplicateExchangeRateException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining(effectiveAt.toString());
    }

    @Test
    @DisplayName("Should translate SQLException with SQLState 23505 and constraint in message into DuplicateExchangeRateException")
    void shouldTranslateSQLExceptionWithSqlState23505AndConstraintInMessage() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);

        SQLException sqlEx = new SQLException("duplicate uk_exchange_rates_currency_effective_at", "23505");
        DataIntegrityViolationException dive = new DataIntegrityViolationException("data integrity", sqlEx);

        when(repository.saveAndFlush(any(ExchangeRate.class))).thenThrow(dive);

        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt))
                .isInstanceOf(DuplicateExchangeRateException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining(effectiveAt.toString());
    }

    @Test
    @DisplayName("Should rethrow DataIntegrityViolationException when constraint matches but SQLState is not 23505")
    void shouldRethrowWhenConstraintMatchesButSqlStateIsNot23505() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);

        SQLException sqlEx = new SQLException("not-null violation", "23502");
        ConstraintViolationException cve = new ConstraintViolationException(
                "not null",
                sqlEx,
                "uk_exchange_rates_currency_effective_at"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("data integrity", cve);

        when(repository.saveAndFlush(any(ExchangeRate.class))).thenThrow(dive);

        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should rethrow DataIntegrityViolationException when SQLState is 23505 but constraint is different")
    void shouldRethrowWhenSqlStateIs23505ButConstraintIsDifferent() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);

        SQLException sqlEx = new SQLException("duplicate", "23505");
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                sqlEx,
                "uk_other_table_constraint"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("data integrity", cve);

        when(repository.saveAndFlush(any(ExchangeRate.class))).thenThrow(dive);

        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should rethrow unrelated DataIntegrityViolationException without masking")
    void shouldRethrowUnrelatedDataIntegrityViolationException() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);

        when(repository.saveAndFlush(any(ExchangeRate.class)))
                .thenThrow(new DataIntegrityViolationException("check constraint failed"));

        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("check constraint failed");
    }

    @Test
    @DisplayName("Should return exchange rate when an eligible quote exists for operationInstant")
    void shouldReturnExchangeRateWhenEligibleQuoteExists() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(100);
        ExchangeRate expected = new ExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt, NOW);

        when(repository.findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(CurrencyCode.USD, NOW))
                .thenReturn(Optional.of(expected));

        ExchangeRate result = service.getExchangeRate(CurrencyCode.USD, NOW);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should throw ExchangeRateUnavailableException when no eligible quote exists")
    void shouldThrowExchangeRateUnavailableExceptionWhenNoEligibleQuoteExists() {
        when(repository.findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(CurrencyCode.USD, NOW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExchangeRate(CurrencyCode.USD, NOW))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining(NOW.toString());
    }

    @Test
    @DisplayName("Should reject null parameters defensively")
    void shouldRejectNullParameters() {
        assertThatThrownBy(() -> service.registerExchangeRate(null, new BigDecimal("5.0"), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, new BigDecimal("5.0"), null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.getExchangeRate(null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.getExchangeRate(CurrencyCode.USD, null))
                .isInstanceOf(NullPointerException.class);
    }
}

