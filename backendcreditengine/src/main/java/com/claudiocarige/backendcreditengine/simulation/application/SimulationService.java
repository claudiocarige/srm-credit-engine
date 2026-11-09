package com.claudiocarige.backendcreditengine.simulation.application;

import com.claudiocarige.backendcreditengine.exchangerate.application.ExchangeRateService;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.pricing.domain.PricingEngine;
import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CommercialTermCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CurrencyConverter;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.simulation.application.exception.InvalidDueDateException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private final PricingEngine pricingEngine;
    private final CommercialTermCalculator commercialTermCalculator;
    private final CurrencyConverter currencyConverter;
    private final ExchangeRateService exchangeRateService;
    private final Clock clock;
    private final BigDecimal baseRateMonthly;

    public SimulationService(
            PricingEngine pricingEngine,
            CommercialTermCalculator commercialTermCalculator,
            CurrencyConverter currencyConverter,
            ExchangeRateService exchangeRateService,
            Clock clock,
            @Value("${srm.pricing.base-rate-monthly:0.010000}") BigDecimal baseRateMonthly
    ) {
        this.pricingEngine = Objects.requireNonNull(pricingEngine, "pricingEngine cannot be null");
        this.commercialTermCalculator = Objects.requireNonNull(commercialTermCalculator, "commercialTermCalculator cannot be null");
        this.currencyConverter = Objects.requireNonNull(currencyConverter, "currencyConverter cannot be null");
        this.exchangeRateService = Objects.requireNonNull(exchangeRateService, "exchangeRateService cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.baseRateMonthly = Objects.requireNonNull(baseRateMonthly, "baseRateMonthly cannot be null");
    }

    public SimulationResult simulate(
            ReceivableType type,
            BigDecimal faceValueBrl,
            LocalDate dueDate,
            CurrencyCode paymentCurrency
    ) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(faceValueBrl, "faceValueBrl cannot be null");
        Objects.requireNonNull(dueDate, "dueDate cannot be null");
        Objects.requireNonNull(paymentCurrency, "paymentCurrency cannot be null");

        Instant simulatedAt = clock.instant();
        LocalDate operationDate = LocalDate.ofInstant(simulatedAt, ZoneOffset.UTC);

        if (!dueDate.isAfter(operationDate)) {
            throw new InvalidDueDateException(operationDate, dueDate);
        }

        int termMonths = commercialTermCalculator.calculateTermMonths(operationDate, dueDate);
        PricingResult pricing = pricingEngine.calculate(new PricingInput(type, faceValueBrl, baseRateMonthly, termMonths));

        if (paymentCurrency == CurrencyCode.BRL) {
            return new SimulationResult(
                    simulatedAt,
                    operationDate,
                    type,
                    dueDate,
                    termMonths,
                    pricing.faceValueBrl(),
                    pricing.baseRateMonthly(),
                    pricing.spreadMonthly(),
                    pricing.presentValueBrl(),
                    pricing.discountBrl(),
                    paymentCurrency,
                    null,
                    null,
                    pricing.presentValueBrl()
            );
        }

        ExchangeRate exchangeRate = exchangeRateService.getExchangeRate(CurrencyCode.USD, simulatedAt);
        BigDecimal paidAmountUsd = currencyConverter.convertToUsd(pricing.presentValueBrl(), exchangeRate.getRateBrl());

        return new SimulationResult(
                simulatedAt,
                operationDate,
                type,
                dueDate,
                termMonths,
                pricing.faceValueBrl(),
                pricing.baseRateMonthly(),
                pricing.spreadMonthly(),
                pricing.presentValueBrl(),
                pricing.discountBrl(),
                paymentCurrency,
                exchangeRate.getRateBrl(),
                exchangeRate.getEffectiveAt(),
                paidAmountUsd
        );
    }
}
