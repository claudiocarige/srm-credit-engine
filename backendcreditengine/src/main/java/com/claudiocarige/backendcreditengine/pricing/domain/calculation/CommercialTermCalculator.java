package com.claudiocarige.backendcreditengine.pricing.domain.calculation;

import com.claudiocarige.backendcreditengine.pricing.domain.PricingValidationException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calculador puro de prazo comercial em meses inteiros (n >= 1) a partir de datas de operação e vencimento.
 * <p>
 * Responsabilidade exclusiva: converter o intervalo em dias corridos entre a data de referência
 * da operação e a data de vencimento em meses comerciais de 30 dias com arredondamento para cima
 * (ceil(dias / 30)), conforme definido no SPEC.md (§3.2) e DEC-005.
 * <p>
 * Este componente opera em Java puro e é desacoplado de infraestrutura, HTTP e banco de dados.
 */
public final class CommercialTermCalculator {

    private static final int DAYS_PER_COMMERCIAL_MONTH = 30;

    /**
     * Calcula o prazo comercial em meses inteiros.
     *
     * @param operationDate data de referência da operação (determinada pelo backend)
     * @param dueDate       data de vencimento do recebível
     * @return prazo comercial em meses inteiros (n >= 1)
     * @throws PricingValidationException se alguma data for nula ou se dueDate não for estritamente posterior a operationDate
     */
    public int calculateTermMonths(LocalDate operationDate, LocalDate dueDate) {
        if (operationDate == null) {
            throw new PricingValidationException("Operation date must not be null");
        }
        if (dueDate == null) {
            throw new PricingValidationException("Due date must not be null");
        }
        if (!dueDate.isAfter(operationDate)) {
            throw new PricingValidationException("Due date must be strictly after operation date");
        }

        long daysBetween = ChronoUnit.DAYS.between(operationDate, dueDate);
        return (int) ((daysBetween + (DAYS_PER_COMMERCIAL_MONTH - 1)) / DAYS_PER_COMMERCIAL_MONTH);
    }
}

