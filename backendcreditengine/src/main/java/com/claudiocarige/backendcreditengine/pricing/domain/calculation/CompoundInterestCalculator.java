package com.claudiocarige.backendcreditengine.pricing.domain.calculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Calculador financeiro puro de juros compostos.
 * <p>
 * Responsabilidade exclusiva: calcular o valor presente em BRL a partir do valor de face,
 * taxa mensal total e prazo em meses, aplicando MathContext.DECIMAL128 nas etapas intermediárias
 * e arredondamento HALF_EVEN para duas casas decimais no resultado final.
 */
public final class CompoundInterestCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int CURRENCY_SCALE = 2;

    /**
     * Calcula o valor presente em BRL com arredondamento HALF_EVEN.
     *
     * @param faceValue       valor de face em BRL
     * @param totalMonthlyRate taxa de juros mensal total (base + spread) em razão decimal
     * @param termMonths      prazo comercial em meses inteiros (n >= 1)
     * @return valor presente em BRL arredondado para 2 casas decimais
     */
    public BigDecimal calculatePresentValue(BigDecimal faceValue, BigDecimal totalMonthlyRate, int termMonths) {
        BigDecimal onePlusRate = BigDecimal.ONE.add(totalMonthlyRate, MC);
        BigDecimal compoundFactor = onePlusRate.pow(termMonths, MC);
        BigDecimal presentValueRaw = faceValue.divide(compoundFactor, MC);
        return presentValueRaw.setScale(CURRENCY_SCALE, RoundingMode.HALF_EVEN);
    }
}

