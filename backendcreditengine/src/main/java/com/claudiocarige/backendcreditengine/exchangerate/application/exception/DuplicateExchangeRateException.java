package com.claudiocarige.backendcreditengine.exchangerate.application.exception;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.time.Instant;

public class DuplicateExchangeRateException extends RuntimeException {

    public DuplicateExchangeRateException(CurrencyCode currency, Instant effectiveAt) {
        super("Exchange rate already exists for currency " + currency + " at effectiveAt " + effectiveAt);
    }
}

