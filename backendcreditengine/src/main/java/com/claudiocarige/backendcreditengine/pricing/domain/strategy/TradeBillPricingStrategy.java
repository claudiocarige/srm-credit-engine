package com.claudiocarige.backendcreditengine.pricing.domain.strategy;

import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CompoundInterestCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Estratégia de precificação para Duplicata Mercantil.
 * <p>
 * Spread mensal: 1,50% a.m. (0.015000).
 * Utiliza o CompoundInterestCalculator por composição.
 */
public final class TradeBillPricingStrategy implements PricingStrategy {

    private static final BigDecimal TRADE_BILL_SPREAD_MONTHLY = new BigDecimal("0.015000");

    private final CompoundInterestCalculator calculator;

    public TradeBillPricingStrategy(CompoundInterestCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "CompoundInterestCalculator must not be null");
    }

    @Override
    public ReceivableType getSupportedType() {
        return ReceivableType.DUPLICATA_MERCANTIL;
    }

    @Override
    public PricingResult calculate(PricingInput input) {
        BigDecimal totalRate = input.baseRateMonthly().add(TRADE_BILL_SPREAD_MONTHLY);
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
            TRADE_BILL_SPREAD_MONTHLY,
            input.termMonths(),
            presentValue,
            discount
        );
    }
}

