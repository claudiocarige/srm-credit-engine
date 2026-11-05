package com.claudiocarige.backendcreditengine.pricing.domain.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CompoundInterestCalculatorTest {

    private final CompoundInterestCalculator calculator = new CompoundInterestCalculator();

    @Test
    @DisplayName("Deve calcular valor presente com fórmula de juros compostos em cenário padrão")
    void shouldCalculatePresentValueStandard() {
        BigDecimal faceValue = new BigDecimal("1000.00");
        BigDecimal totalRate = new BigDecimal("0.050000");
        int termMonths = 1;

        BigDecimal presentValue = calculator.calculatePresentValue(faceValue, totalRate, termMonths);

        assertEquals(new BigDecimal("952.38"), presentValue);
    }

    @Test
    @DisplayName("Deve demonstrar a diferença entre HALF_EVEN e HALF_UP em empates exatos (.XX5)")
    void shouldDemonstrateHalfEvenVsHalfUpExactTie() {
        BigDecimal tieEven = new BigDecimal("1.025");
        BigDecimal halfEvenResult = tieEven.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal halfUpResult = tieEven.setScale(2, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("1.02"), halfEvenResult);
        assertEquals(new BigDecimal("1.03"), halfUpResult);
        assertNotEquals(halfEvenResult, halfUpResult);

        BigDecimal tieOdd = new BigDecimal("1.035");
        assertEquals(new BigDecimal("1.04"), tieOdd.setScale(2, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("1.04"), tieOdd.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Deve aplicar HALF_EVEN no cálculo financeiro completo de ponta a ponta em situação de empate exato")
    void shouldApplyHalfEvenInFullFinancialCalculation() {

        BigDecimal faceValue = new BigDecimal("1.23");
        BigDecimal totalRate = new BigDecimal("0.200000");
        int termMonths = 1;

        BigDecimal presentValue = calculator.calculatePresentValue(faceValue, totalRate, termMonths);

        assertEquals(new BigDecimal("1.02"), presentValue);
    }
}

