package com.claudiocarige.backendcreditengine.exchangerate.persistence;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jcajce.provider.symmetric.util.IvAlgorithmParameters;

import java.lang.foreign.ValueLayout;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeRateTest {

        private static final Instant VALID_INSTANT = Instant.parse("2026-11-06T10:15:30.123456Z");

        @Test
        @DisplayName("Deve preservar effectiveAt e truncar createdAt para microssegundos")
        void shouldCreateExchangeRateSuccessfully() {

                Instant effectiveAt = Instant.parse(
                                "2026-11-06T10:15:30.123456Z");

                Instant createdAt = Instant.parse(
                                "2026-11-06T10:15:35.987654321Z");

                ExchangeRate exchangeRate = new ExchangeRate(
                                CurrencyCode.USD,
                                new BigDecimal("5.432100"),
                                effectiveAt,
                                createdAt);

                assertThat(exchangeRate.getCurrency())
                                .isEqualTo(CurrencyCode.USD);

                assertThat(exchangeRate.getRateBrl())
                                .isEqualByComparingTo("5.432100");

                assertThat(exchangeRate.getEffectiveAt())
                                .isEqualTo(effectiveAt);

                assertThat(exchangeRate.getCreatedAt())
                                .isEqualTo(createdAt.truncatedTo(ChronoUnit.MICROS));
        }

        @Test
        @DisplayName("Deve aceitar cotações nos limites contratuais [0.000001, 9999999.999999]")
        void shouldAcceptBoundaryRates() {
                Instant now = VALID_INSTANT;

                ExchangeRate minRate = new ExchangeRate(CurrencyCode.USD, new BigDecimal("0.000001"), now, now);
                assertThat(minRate.getRateBrl()).isEqualByComparingTo("0.000001");

                ExchangeRate maxRate = new ExchangeRate(CurrencyCode.USD, new BigDecimal("9999999.999999"), now, now);
                assertThat(maxRate.getRateBrl()).isEqualByComparingTo("9999999.999999");
        }

        @Test
        @DisplayName("Deve instantes com precisão incompatível")
        void shouldRejectEffectiveAtWithNanosecondPrecision() {

                Instant effectiveAt = Instant.parse(
                        "2026-11-06T10:30:00.123456789Z"
                );

                assertThatThrownBy(() -> new ExchangeRate(
                        CurrencyCode.USD,
                        new BigDecimal("5.432100"),
                        effectiveAt,
                        VALID_INSTANT
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
        }

        @Test
        @DisplayName("Deve rejeitar moeda diferente de USD")
        void shouldRejectCurrencyOtherThanUsd() {
                Instant now = VALID_INSTANT;

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.BRL, new BigDecimal("5.000000"), now, now))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("currency must be USD");
        }

        @Test
        @DisplayName("Deve rejeitar escala cambial superior a 6 casas decimais, inclusive com zeros adicionais")
        void shouldRejectRateWithScaleGreaterThanSix() {
                Instant now = VALID_INSTANT;

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, new BigDecimal("5.1234560"), now, now))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("scale cannot exceed 6 decimal places");

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, new BigDecimal("5.1234567"), now, now))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("scale cannot exceed 6 decimal places");
        }

        @Test
        @DisplayName("Deve rejeitar taxa de câmbio fora do intervalo permitido [0.000001, 9999999.999999]")
        void shouldRejectRateOutOfRange() {
                Instant now = VALID_INSTANT;

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, new BigDecimal("0.000000"), now, now))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("rateBrl must be between 0.000001 and 9999999.999999");

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, new BigDecimal("-1.000000"), now, now))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("rateBrl must be between 0.000001 and 9999999.999999");

                assertThatThrownBy(
                                () -> new ExchangeRate(CurrencyCode.USD, new BigDecimal("10000000.000000"), now, now))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("rateBrl must be between 0.000001 and 9999999.999999");
        }

        @Test
        @DisplayName("Deve rejeitar campos obrigatórios nulos")
        void shouldRejectNullFields() {
                Instant now = VALID_INSTANT;
                BigDecimal validRate = new BigDecimal("5.000000");

                assertThatThrownBy(() -> new ExchangeRate(null, validRate, now, now))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("currency cannot be null");

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, null, now, now))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("rateBrl cannot be null");

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, validRate, null, now))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("effectiveAt cannot be null");

                assertThatThrownBy(() -> new ExchangeRate(CurrencyCode.USD, validRate, now, null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("createdAt cannot be null");
        }
}
