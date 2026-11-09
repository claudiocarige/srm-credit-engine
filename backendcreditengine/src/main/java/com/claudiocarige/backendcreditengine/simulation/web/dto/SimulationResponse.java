package com.claudiocarige.backendcreditengine.simulation.web.dto;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.simulation.application.SimulationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

public record SimulationResponse(
        Instant simulatedAt,
        LocalDate operationDate,
        ReceivableType type,
        LocalDate dueDate,
        int termMonths,
        String faceValueBrl,
        String baseRateMonthly,
        String spreadMonthly,
        String presentValueBrl,
        String discountBrl,
        CurrencyCode paymentCurrency,
        String exchangeRateApplied,
        Instant exchangeRateEffectiveAt,
        String paidAmount
) {

    public static SimulationResponse from(SimulationResult result) {
        return new SimulationResponse(
                result.simulatedAt(),
                result.operationDate(),
                result.type(),
                result.dueDate(),
                result.termMonths(),
                formatMonetary(result.faceValueBrl()),
                formatRate(result.baseRateMonthly()),
                formatRate(result.spreadMonthly()),
                formatMonetary(result.presentValueBrl()),
                formatMonetary(result.discountBrl()),
                result.paymentCurrency(),
                result.exchangeRateApplied() != null ? formatRate(result.exchangeRateApplied()) : null,
                result.exchangeRateEffectiveAt(),
                formatMonetary(result.paidAmount())
        );
    }

    private static String formatMonetary(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatRate(BigDecimal value) {
        return value.setScale(6, RoundingMode.UNNECESSARY).toPlainString();
    }
}
