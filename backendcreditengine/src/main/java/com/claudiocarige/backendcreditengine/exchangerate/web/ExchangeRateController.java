package com.claudiocarige.backendcreditengine.exchangerate.web;

import com.claudiocarige.backendcreditengine.exchangerate.application.ExchangeRateService;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.InvalidCurrencyException;
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
            @RequestParam(name = "currency", required = false, defaultValue = "USD") String currency
    ) {
        if (!"USD".equals(currency)) {
            throw new InvalidCurrencyException("currency must be USD");
        }

        ExchangeRate rate = exchangeRateService.getExchangeRate(CurrencyCode.USD, clock.instant());
        return ResponseEntity.ok(ExchangeRateResponse.fromDomain(rate));
    }
}

