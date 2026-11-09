package com.claudiocarige.backendcreditengine.simulation.application;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SimulationResult(
    Instant simulatedAt,
    LocalDate operationDate,
    ReceivableType type,
    LocalDate dueDate,
    int termMonths,
    BigDecimal faceValueBrl,
    BigDecimal baseRateMonthly,
    BigDecimal spreadMonthly,
    BigDecimal presentValueBrl,
    BigDecimal discountBrl,
    CurrencyCode paymentCurrency,
    BigDecimal exchangeRateApplied,
    Instant exchangeRateEffectiveAt,
    BigDecimal paidAmount
) {}
