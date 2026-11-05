package com.claudiocarige.backendcreditengine.pricing.domain.calculation;

import com.claudiocarige.backendcreditengine.pricing.domain.PricingValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CurrencyConverter — Testes unitários de conversão cambial pura")
class CurrencyConverterTest {

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter();
    }

    @Test
    @DisplayName("Golden Case C3: Deve converter VP R$ 92.859,94 com cotação 5.432100 exatamente para US$ 17.094,67")
    void shouldConvertGoldenCaseC3PresentValueToUsd() {
        BigDecimal presentValueBrl = new BigDecimal("92859.94");
        BigDecimal exchangeRate = new BigDecimal("5.432100");

        BigDecimal paidAmountUsd = converter.convertToUsd(presentValueBrl, exchangeRate);

        assertEquals(new BigDecimal("17094.67"), paidAmountUsd);
    }

    @Test
    @DisplayName("Golden Case C3: Deve produzir o mesmo resultado com cotação 5.4321 (sem zeros à direita)")
    void shouldConvertGoldenCaseC3WithFourDecimalPlacesRate() {
        BigDecimal presentValueBrl = new BigDecimal("92859.94");
        BigDecimal exchangeRate = new BigDecimal("5.4321");

        BigDecimal paidAmountUsd = converter.convertToUsd(presentValueBrl, exchangeRate);

        assertEquals(new BigDecimal("17094.67"), paidAmountUsd);
    }

    @Test
    @DisplayName("Anti-arredondamento prematuro: Deve comprovar divergência entre converter VP bruto (100.024) vs VP arredondado (100.02)")
    void shouldDemonstrateDivergenceWhenConvertingRawVsRoundedPresentValue() {
        BigDecimal rawPresentValue = new BigDecimal("100.024");
        BigDecimal roundedPresentValue = new BigDecimal("100.02");
        BigDecimal rate = new BigDecimal("0.500000");

        BigDecimal correctUsd = converter.convertToUsd(roundedPresentValue, rate);
        assertEquals(new BigDecimal("200.04"), correctUsd);

        BigDecimal directRawUsd = rawPresentValue.divide(rate, MathContext.DECIMAL128)
                .setScale(2, RoundingMode.HALF_EVEN);
        assertEquals(new BigDecimal("200.05"), directRawUsd);

        assertNotEquals(correctUsd, directRawUsd, "A conversão do VP bruto deve divergir da conversão do VP arredondado");
    }

    @Test
    @DisplayName("Deve rejeitar valor presente BRL com escala maior que 2 (defesa contra VP bruto não arredondado)")
    void shouldRejectPresentValueWithScaleGreaterThanTwo() {
        BigDecimal unroundedPresentValue = new BigDecimal("100.024");
        BigDecimal rate = new BigDecimal("0.500000");

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(unroundedPresentValue, rate)
        );

        assertEquals(
            "Present value BRL must be previously rounded to at most 2 decimal places",
            exception.getMessage()
        );
    }

    @Test
    @DisplayName("Arredondamento HALF_EVEN: Deve arredondar para o par mais próximo em empates exatos (.XX5)")
    void shouldApplyHalfEvenRoundingInExactTies() {
        BigDecimal rate = new BigDecimal("2.000000");

        BigDecimal roundToEven = converter.convertToUsd(new BigDecimal("2.05"), rate);
        assertEquals(new BigDecimal("1.02"), roundToEven);

        BigDecimal roundToOdd = converter.convertToUsd(new BigDecimal("2.07"), rate);
        assertEquals(new BigDecimal("1.04"), roundToOdd);
    }

    @Test
    @DisplayName("Deve aceitar cotação no limite mínimo permitido (0.000001)")
    void shouldAcceptMinimumRate() {
        BigDecimal presentValue = new BigDecimal("1.00");
        BigDecimal minRate = new BigDecimal("0.000001");

        BigDecimal result = converter.convertToUsd(presentValue, minRate);
        assertEquals(new BigDecimal("1000000.00"), result);
    }

    @Test
    @DisplayName("Deve aceitar cotação no limite máximo permitido (9999999.999999)")
    void shouldAcceptMaximumRate() {
        BigDecimal presentValue = new BigDecimal("1000000.00");
        BigDecimal maxRate = new BigDecimal("9999999.999999");

        BigDecimal result = converter.convertToUsd(presentValue, maxRate);
        assertEquals(new BigDecimal("0.10"), result);
    }

    @ParameterizedTest(name = "Deve rejeitar cotação abaixo do mínimo: {0}")
    @ValueSource(strings = {"0.000000", "-0.000001", "-1.000000", "-100.00"})
    void shouldThrowWhenRateIsBelowMinimum(String invalidRate) {
        BigDecimal presentValue = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal(invalidRate);

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(presentValue, rate)
        );

        assertEquals("Exchange rate must be between 0.000001 and 9999999.999999", exception.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar cotação acima do limite máximo permitido")
    void shouldThrowWhenRateExceedsMaximum() {
        BigDecimal presentValue = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal("10000000.000000");

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(presentValue, rate)
        );

        assertEquals("Exchange rate must be between 0.000001 and 9999999.999999", exception.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar cotação com escala maior que 6 casas decimais")
    void shouldThrowWhenRateScaleExceedsSix() {
        BigDecimal presentValue = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal("5.4321001"); // 7 casas decimais

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(presentValue, rate)
        );

        assertEquals("Exchange rate scale must not exceed 6 decimal places", exception.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar valor presente BRL nulo")
    void shouldThrowWhenPresentValueIsNull() {
        BigDecimal rate = new BigDecimal("5.432100");

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(null, rate)
        );

        assertEquals("Present value BRL must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar valor presente BRL negativo")
    void shouldThrowWhenPresentValueIsNegative() {
        BigDecimal negativeVp = new BigDecimal("-10.00");
        BigDecimal rate = new BigDecimal("5.432100");

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(negativeVp, rate)
        );

        assertEquals("Present value BRL must be non-negative", exception.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar cotação nula")
    void shouldThrowWhenRateIsNull() {
        BigDecimal presentValue = new BigDecimal("100.00");

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> converter.convertToUsd(presentValue, null)
        );

        assertEquals("Exchange rate must not be null", exception.getMessage());
    }
}