package com.claudiocarige.backendcreditengine.exchangerate.bootstrap;

import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRateRepository;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class ExchangeRateDevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateDevDataInitializer.class);

    private static final CurrencyCode DEV_CURRENCY = CurrencyCode.USD;
    private static final BigDecimal DEV_RATE = new BigDecimal("5.432100");
    private static final Instant DEV_EFFECTIVE_AT = Instant.parse("2026-11-04T00:00:00Z");

    private final ExchangeRateRepository repository;
    private final Clock clock;

    public ExchangeRateDevDataInitializer(ExchangeRateRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = repository.existsByCurrencyAndEffectiveAt(DEV_CURRENCY, DEV_EFFECTIVE_AT);

        if (!exists) {
            ExchangeRate seedRate = new ExchangeRate(
                    DEV_CURRENCY,
                    DEV_RATE,
                    DEV_EFFECTIVE_AT,
                    clock.instant()
            );
            repository.saveAndFlush(seedRate);
            log.info("Initialized development exchange rate: {} = {} effective at {}",
                    DEV_CURRENCY, DEV_RATE, DEV_EFFECTIVE_AT);
        } else {
            log.debug("Development exchange rate already present; skipping seed.");
        }
    }
}

