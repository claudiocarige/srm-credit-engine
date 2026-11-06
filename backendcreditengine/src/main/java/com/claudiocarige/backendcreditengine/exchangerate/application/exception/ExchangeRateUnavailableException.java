package com.claudiocarige.backendcreditengine.exchangerate.application.exception;

import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.time.Instant;

public class ExchangeRateUnavailableException extends RuntimeException {

    public ExchangeRateUnavailableException(CurrencyCode currency, Instant operationInstant) {
        super("No exchange rate available for currency " + currency + " at instant " + operationInstant);
    }
}

