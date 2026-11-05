package com.claudiocarige.backendcreditengine.pricing.domain.calculation;

import com.claudiocarige.backendcreditengine.pricing.domain.PricingValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Conversor financeiro puro de BRL para USD.
 * <p>
 * Responsabilidade exclusiva: converter o valor presente em BRL previamente arredondado
 * para USD dividindo-o pela cotação cambial BRL/USD, aplicando MathContext.DECIMAL128
 * nas etapas intermediárias e arredondamento HALF_EVEN para duas casas decimais no resultado final.
 * <p>
 * Valida defensivamente os limites contratuais da taxa cambial e assegura que apenas valores
 * em BRL previamente arredondados (escala máxima 2) sejam aceitos.
 */
public final class CurrencyConverter {

    private static final BigDecimal MIN_RATE = new BigDecimal("0.000001");
    private static final BigDecimal MAX_RATE = new BigDecimal("9999999.999999");
    private static final int MAX_RATE_SCALE = 6;
    private static final int MAX_PRESENT_VALUE_SCALE = 2;
    private static final int CURRENCY_SCALE = 2;

    /**
     * Converte o valor presente em BRL já arredondado para USD com arredondamento HALF_EVEN.
     *
     * @param presentValueBrl valor presente em BRL já arredondado (escala máxima de 2 casas decimais)
     * @param rateBrl         cotação cambial expressa como BRL por 1 USD
     * @return valor liquidado em USD arredondado para duas casas decimais com HALF_EVEN
     * @throws PricingValidationException se os parâmetros forem nulos, fora dos limites ou se presentValueBrl não estiver arredondado
     */
    public BigDecimal convertToUsd(BigDecimal presentValueBrl, BigDecimal rateBrl) {
        validateInputs(presentValueBrl, rateBrl);

        BigDecimal rawUsd = presentValueBrl.divide(rateBrl, MathContext.DECIMAL128);
        return rawUsd.setScale(CURRENCY_SCALE, RoundingMode.HALF_EVEN);
    }

    private void validateInputs(BigDecimal presentValueBrl, BigDecimal rateBrl) {
        if (presentValueBrl == null) {
            throw new PricingValidationException("Present value BRL must not be null");
        }
        if (presentValueBrl.signum()< 0) {
            throw new PricingValidationException("Present value BRL must be non-negative");
        }
        if (presentValueBrl.scale() > MAX_PRESENT_VALUE_SCALE) {
            throw new PricingValidationException(
                "Present value BRL must be previously rounded to at most " + MAX_PRESENT_VALUE_SCALE + " decimal places"
            );
        }

        if (rateBrl == null) {
            throw new PricingValidationException("Exchange rate must not be null");
        }
        if (rateBrl.scale() > MAX_RATE_SCALE) {
            throw new PricingValidationException(
                "Exchange rate scale must not exceed " + MAX_RATE_SCALE + " decimal places"
            );
        }
        if (rateBrl.compareTo(MIN_RATE) < 0 || rateBrl.compareTo(MAX_RATE) > 0) {
            throw new PricingValidationException(
                "Exchange rate must be between " + MIN_RATE + " and " + MAX_RATE
            );
        }
    }
}
