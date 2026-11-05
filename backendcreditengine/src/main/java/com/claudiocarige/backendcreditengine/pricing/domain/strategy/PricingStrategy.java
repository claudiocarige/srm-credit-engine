package com.claudiocarige.backendcreditengine.pricing.domain.strategy;

import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;

/**
 * Contrato aberto para estratégias de precificação de recebíveis (Strategy Pattern).
 * <p>
 * Cada implementação encapsula a definição do seu spread e o algoritmo de precificação
 * do ativo correspondente, retornando o PricingResult detalhado.
 */
public interface PricingStrategy {

    /**
     * Retorna o tipo de recebível suportado por esta estratégia.
     */
    ReceivableType getSupportedType();

    /**
     * Executa a precificação do recebível.
     *
     * @param input parâmetros de entrada validados
     * @return resultado da precificação com VP, deságio e taxas aplicadas
     */
    PricingResult calculate(PricingInput input);
}
