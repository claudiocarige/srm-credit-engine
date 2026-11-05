package com.claudiocarige.backendcreditengine.pricing.domain.strategy;

import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CompoundInterestCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Estratégia de precificação para Cheque Pré-datado.
 * <p>
 * Spread mensal: 2,50% a.m. (0.025000).
 * Utiliza o CompoundInterestCalculator por composição.
 */
public final class PostDatedCheckPricingStrategy implements PricingStrategy {

    private static final BigDecimal POST_DATED_CHECK_SPREAD_MONTHLY  = new BigDecimal("0.025000");

    private final CompoundInterestCalculator calculator;

    public PostDatedCheckPricingStrategy(CompoundInterestCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "CompoundInterestCalculator must not be null");
    }

    @Override
    public ReceivableType getSupportedType() {
        return ReceivableType.CHEQUE_PRE_DATADO;
    }

    @Override
    public PricingResult calculate(PricingInput input) {
        BigDecimal totalRate = input.baseRateMonthly().add(POST_DATED_CHECK_SPREAD_MONTHLY );
        BigDecimal presentValue = calculator.calculatePresentValue(
            input.faceValueBrl(),
            totalRate,
            input.termMonths()
        );
        BigDecimal discount = input.faceValueBrl().subtract(presentValue);

        return new PricingResult(
            getSupportedType(),
            input.faceValueBrl(),
            input.baseRateMonthly(),
            POST_DATED_CHECK_SPREAD_MONTHLY ,
            input.termMonths(),
            presentValue,
            discount
        );
    }
}

