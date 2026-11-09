package com.claudiocarige.backendcreditengine.config;

import com.claudiocarige.backendcreditengine.pricing.domain.PricingEngine;
import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CommercialTermCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CurrencyConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PricingConfig {

    @Bean
    public PricingEngine pricingEngine() {
        return new PricingEngine();
    }

    @Bean
    public CommercialTermCalculator commercialTermCalculator() {
        return new CommercialTermCalculator();
    }

    @Bean
    public CurrencyConverter currencyConverter() {
        return new CurrencyConverter();
    }
}
