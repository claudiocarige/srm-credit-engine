package com.claudiocarige.backendcreditengine.exchangerate.web;

import com.claudiocarige.backendcreditengine.exchangerate.application.ExchangeRateService;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.exchangerate.web.dto.CreateExchangeRateRequest;
import com.claudiocarige.backendcreditengine.exchangerate.web.dto.ExchangeRateResponse;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final Clock clock;

    public ExchangeRateController(ExchangeRateService exchangeRateService, Clock clock) {
        this.exchangeRateService = Objects.requireNonNull(exchangeRateService, "exchangeRateService cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExchangeRateResponse> createExchangeRate(@Valid @RequestBody CreateExchangeRateRequest request) {
        BigDecimal rateBrl = new BigDecimal(request.rateBrl());
        ExchangeRate saved = exchangeRateService.registerExchangeRate(
                request.currency(),
                rateBrl,
                request.effectiveAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ExchangeRateResponse.fromDomain(saved));
    }

    @GetMapping(value = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExchangeRateResponse> getCurrentExchangeRate(
            @RequestParam(name = "currency", required = false, defaultValue = "USD") CurrencyCode currency
    ) {
        if (currency != CurrencyCode.USD) {
            throw new MethodArgumentTypeMismatchException(currency, CurrencyCode.class, "currency", null, null);
        }

        ExchangeRate rate = exchangeRateService.getExchangeRate(currency, clock.instant());
        return ResponseEntity.ok(ExchangeRateResponse.fromDomain(rate));
    }
}

