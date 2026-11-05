package com.claudiocarige.backendcreditengine.pricing.domain.model;

import java.math.BigDecimal;

/**
 * Parâmetros de entrada imutáveis para precificação de recebíveis em BRL.
 *
 * @param type            tipo do recebível
 * @param faceValueBrl    valor nominal em BRL
 * @param baseRateMonthly taxa de juros base mensal em razão decimal (ex.: 0.010000 = 1,00% a.m.)
 * @param termMonths      prazo em meses inteiros (n >= 1)
 */
public record PricingInput(
    ReceivableType type,
    BigDecimal faceValueBrl,
    BigDecimal baseRateMonthly,
    int termMonths
) {}

