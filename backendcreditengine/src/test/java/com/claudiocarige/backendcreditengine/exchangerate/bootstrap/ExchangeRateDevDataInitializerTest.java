package com.claudiocarige.backendcreditengine.exchangerate.bootstrap;

import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRateRepository;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateDevDataInitializerTest {

    private static final Instant SEED_EFFECTIVE_AT = Instant.parse("2026-11-04T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Mock
    private ExchangeRateRepository repository;

    private Clock fixedClock;
    private ExchangeRateDevDataInitializer initializer;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        initializer = new ExchangeRateDevDataInitializer(repository, fixedClock);
    }

    @Test
    @DisplayName("Cotação anterior não deve impedir o cadastro da cotação demonstrativa")
    void shouldRegisterSeedEvenWhenPriorQuoteExists() {
        when(repository.existsByCurrencyAndEffectiveAt(CurrencyCode.USD, SEED_EFFECTIVE_AT))
                .thenReturn(false);

        initializer.run(null);

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(repository).saveAndFlush(captor.capture());

        ExchangeRate saved = captor.getValue();
        assertThat(saved.getCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(saved.getRateBrl()).isEqualByComparingTo(new BigDecimal("5.432100"));
        assertThat(saved.getEffectiveAt()).isEqualTo(SEED_EFFECTIVE_AT);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Não deve cadastrar seed caso a cotação demonstrativa exata já exista")
    void shouldSkipSeedWhenExactQuoteAlreadyExists() {
        when(repository.existsByCurrencyAndEffectiveAt(CurrencyCode.USD, SEED_EFFECTIVE_AT))
                .thenReturn(true);

        initializer.run(null);

        verify(repository, never()).saveAndFlush(any());
    }
}

