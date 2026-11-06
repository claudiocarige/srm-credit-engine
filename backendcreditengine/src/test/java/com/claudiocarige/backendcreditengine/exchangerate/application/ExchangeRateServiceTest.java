package com.claudiocarige.backendcreditengine.exchangerate.application;

import com.claudiocarige.backendcreditengine.exchangerate.application.exception.DuplicateExchangeRateException;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.ExchangeRateUnavailableException;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRateRepository;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
    @DisplayName("Should translate uniqueness constraint violation into DuplicateExchangeRateException")
    void shouldTranslateUniqueConstraintViolationIntoDuplicateExchangeRateException() {
        BigDecimal rateBrl = new BigDecimal("5.432100");
        Instant effectiveAt = NOW.minusSeconds(3600);

        when(repository.saveAndFlush(any(ExchangeRate.class)))
                .thenThrow(new DataIntegrityViolationException("violates unique constraint \"uk_exchange_rates_currency_effective_at\""));

        assertThatThrownBy(() -> service.registerExchangeRate(CurrencyCode.USD, rateBrl, effectiveAt))
                .isInstanceOf(DuplicateExchangeRateException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining(effectiveAt.toString());
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

