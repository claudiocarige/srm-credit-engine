package com.claudiocarige.backendcreditengine.pricing.domain.model;

import java.math.BigDecimal;

/**
 * Resultado detalhado e imutável da precificação de recebíveis em BRL.
 *
 * @param type            tipo do recebível
 * @param faceValueBrl    valor nominal em BRL
 * @param baseRateMonthly taxa base mensal aplicada
 * @param spreadMonthly   taxa de spread mensal aplicada
 * @param termMonths      prazo em meses inteiros utilizado
 * @param presentValueBrl valor presente em BRL com arredondamento HALF_EVEN (2 casas)
 * @param discountBrl     deságio em BRL obtido por subtração exata (faceValueBrl - presentValueBrl)
 */
public record PricingResult(
    ReceivableType type,
    BigDecimal faceValueBrl,
    BigDecimal baseRateMonthly,
    BigDecimal spreadMonthly,
    int termMonths,
    BigDecimal presentValueBrl,
    BigDecimal discountBrl
) {}

