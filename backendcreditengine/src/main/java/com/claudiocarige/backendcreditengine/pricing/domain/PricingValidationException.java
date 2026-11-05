package com.claudiocarige.backendcreditengine.pricing.domain;

/**
 * Exceção de domínio lançada quando parâmetros ou limites de precificação são violados.
 */
public class PricingValidationException extends RuntimeException {

    public PricingValidationException(String message) {
        super(message);
    }
}

