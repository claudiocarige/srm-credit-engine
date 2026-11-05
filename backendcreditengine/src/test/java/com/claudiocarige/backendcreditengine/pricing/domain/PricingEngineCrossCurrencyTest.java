package com.claudiocarige.backendcreditengine.pricing.domain;

import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CurrencyConverter;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PricingEngineCrossCurrencyTest — Testes de integração do Golden Case C3")
class PricingEngineCrossCurrencyTest {

    private PricingEngine pricingEngine;
    private CurrencyConverter currencyConverter;

    @BeforeEach
    void setUp() {
        pricingEngine = new PricingEngine();
        currencyConverter = new CurrencyConverter();
    }

    @Test
    @DisplayName("Golden Case C3: Duplicata Mercantil (100k, 3 meses, USD 5.432100) deve produzir VP USD 17.094,67 e Deságio BRL 7.140,06")
    void shouldCalculateGoldenCaseC3CrossCurrency() {
        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            new BigDecimal("0.010000"), 
            3                          
        );
        BigDecimal exchangeRate = new BigDecimal("5.432100");

        PricingResult brlResult = pricingEngine.calculate(input);

        assertEquals(new BigDecimal("92859.94"), brlResult.presentValueBrl(), "Valor presente em BRL deve ser exato");
        assertEquals(new BigDecimal("7140.06"), brlResult.discountBrl(), "Deságio em BRL deve ser exato");
        assertEquals(new BigDecimal("0.015000"), brlResult.spreadMonthly(), "Spread mensal da duplicata deve ser 1,50% a.m.");

        BigDecimal paidAmountUsd = currencyConverter.convertToUsd(brlResult.presentValueBrl(), exchangeRate);

        assertEquals(new BigDecimal("17094.67"), paidAmountUsd, "Valor liquidado em USD (C3) deve ser exatamente US$ 17.094,67");
        assertEquals(new BigDecimal("7140.06"), brlResult.discountBrl(), "Deságio permanece obrigatoriamente expresso em BRL");
    }

    @Test
    @DisplayName("Garantia de Não-Regressão: Golden Cases C1, C2 e C3 reproduzidos conjuntamente")
    void shouldVerifyAllGoldenCasesCoexistence() {
        PricingResult c1 = pricingEngine.calculate(new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            new BigDecimal("0.010000"),
            3
        ));
        assertEquals(new BigDecimal("92859.94"), c1.presentValueBrl());
        assertEquals(new BigDecimal("7140.06"), c1.discountBrl());

        PricingResult c2 = pricingEngine.calculate(new PricingInput(
            ReceivableType.CHEQUE_PRE_DATADO,
            new BigDecimal("25000.00"),
            new BigDecimal("0.010000"),
            2
        ));
        assertEquals(new BigDecimal("23337.77"), c2.presentValueBrl());
        assertEquals(new BigDecimal("1662.23"), c2.discountBrl());

        BigDecimal c3Usd = currencyConverter.convertToUsd(c1.presentValueBrl(), new BigDecimal("5.432100"));
        assertEquals(new BigDecimal("17094.67"), c3Usd);
    }
}
