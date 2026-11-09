package com.claudiocarige.backendcreditengine.simulation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.claudiocarige.backendcreditengine.exchangerate.application.ExchangeRateService;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.ExchangeRateUnavailableException;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.pricing.domain.PricingEngine;
import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CommercialTermCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CurrencyConverter;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.common.exception.InvalidDueDateException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:25:00Z");
    private static final LocalDate OPERATION_DATE = LocalDate.of(2026, 11, 4);
    private static final BigDecimal BASE_RATE_MONTHLY = new BigDecimal("0.010000");

    @Mock
    private ExchangeRateService exchangeRateService;

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        simulationService = new SimulationService(
                new PricingEngine(),
                new CommercialTermCalculator(),
                new CurrencyConverter(),
                exchangeRateService,
                fixedClock,
                BASE_RATE_MONTHLY
        );
    }

    @Test
    @DisplayName("Equivalente integrado ao Golden Case C1: Duplicata Mercantil R$ 100.000,00 com 90 dias (3 meses) em BRL")
    void shouldSimulateGoldenCaseC1EquivalentInBrl() {
        LocalDate dueDate = LocalDate.of(2027, 2, 2); // 90 dias corridos -> 3 meses comerciais

        SimulationResult result = simulationService.simulate(
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                dueDate,
                CurrencyCode.BRL
        );

        assertThat(result.simulatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(result.operationDate()).isEqualTo(OPERATION_DATE);
        assertThat(result.type()).isEqualTo(ReceivableType.DUPLICATA_MERCANTIL);
        assertThat(result.dueDate()).isEqualTo(dueDate);
        assertThat(result.termMonths()).isEqualTo(3);
        assertThat(result.faceValueBrl()).isEqualByComparingTo("100000.00");
        assertThat(result.baseRateMonthly()).isEqualByComparingTo("0.010000");
        assertThat(result.spreadMonthly()).isEqualByComparingTo("0.015000");
        assertThat(result.presentValueBrl()).isEqualByComparingTo("92859.94");
        assertThat(result.discountBrl()).isEqualByComparingTo("7140.06");
        assertThat(result.paymentCurrency()).isEqualTo(CurrencyCode.BRL);
        assertThat(result.exchangeRateApplied()).isNull();
        assertThat(result.exchangeRateEffectiveAt()).isNull();
        assertThat(result.paidAmount()).isEqualByComparingTo("92859.94");

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("Equivalente integrado ao Golden Case C2: Cheque Pré-datado R$ 25.000,00 com 60 dias (2 meses) em BRL")
    void shouldSimulateGoldenCaseC2EquivalentInBrl() {
        LocalDate dueDate = LocalDate.of(2027, 1, 3); // 60 dias corridos -> 2 meses comerciais

        SimulationResult result = simulationService.simulate(
                ReceivableType.CHEQUE_PRE_DATADO,
                new BigDecimal("25000.00"),
                dueDate,
                CurrencyCode.BRL
        );

        assertThat(result.simulatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(result.operationDate()).isEqualTo(OPERATION_DATE);
        assertThat(result.type()).isEqualTo(ReceivableType.CHEQUE_PRE_DATADO);
        assertThat(result.dueDate()).isEqualTo(dueDate);
        assertThat(result.termMonths()).isEqualTo(2);
        assertThat(result.faceValueBrl()).isEqualByComparingTo("25000.00");
        assertThat(result.baseRateMonthly()).isEqualByComparingTo("0.010000");
        assertThat(result.spreadMonthly()).isEqualByComparingTo("0.025000");
        assertThat(result.presentValueBrl()).isEqualByComparingTo("23337.77");
        assertThat(result.discountBrl()).isEqualByComparingTo("1662.23");
        assertThat(result.paymentCurrency()).isEqualTo(CurrencyCode.BRL);
        assertThat(result.exchangeRateApplied()).isNull();
        assertThat(result.exchangeRateEffectiveAt()).isNull();
        assertThat(result.paidAmount()).isEqualByComparingTo("23337.77");

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("Equivalente integrado ao Golden Case C3: Duplicata Mercantil R$ 100.000,00 com 90 dias (3 meses) em USD")
    void shouldSimulateGoldenCaseC3EquivalentInUsd() {
        LocalDate dueDate = LocalDate.of(2027, 2, 2); // 90 dias corridos -> 3 meses comerciais
        Instant effectiveAt = Instant.parse("2026-11-04T12:00:00Z");
        ExchangeRate usdRate = new ExchangeRate(
                CurrencyCode.USD,
                new BigDecimal("5.432100"),
                effectiveAt,
                effectiveAt
        );
        when(exchangeRateService.getExchangeRate(CurrencyCode.USD, FIXED_INSTANT)).thenReturn(usdRate);

        SimulationResult result = simulationService.simulate(
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                dueDate,
                CurrencyCode.USD
        );

        assertThat(result.simulatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(result.operationDate()).isEqualTo(OPERATION_DATE);
        assertThat(result.type()).isEqualTo(ReceivableType.DUPLICATA_MERCANTIL);
        assertThat(result.dueDate()).isEqualTo(dueDate);
        assertThat(result.termMonths()).isEqualTo(3);
        assertThat(result.faceValueBrl()).isEqualByComparingTo("100000.00");
        assertThat(result.baseRateMonthly()).isEqualByComparingTo("0.010000");
        assertThat(result.spreadMonthly()).isEqualByComparingTo("0.015000");
        assertThat(result.presentValueBrl()).isEqualByComparingTo("92859.94");
        assertThat(result.discountBrl()).isEqualByComparingTo("7140.06");
        assertThat(result.paymentCurrency()).isEqualTo(CurrencyCode.USD);
        assertThat(result.exchangeRateApplied()).isEqualByComparingTo("5.432100");
        assertThat(result.exchangeRateEffectiveAt()).isEqualTo(effectiveAt);
        assertThat(result.paidAmount()).isEqualByComparingTo("17094.67");

        verify(exchangeRateService).getExchangeRate(CurrencyCode.USD, FIXED_INSTANT);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-05, 1",
            "2026-12-04, 1",
            "2026-12-05, 2"
    })
    @DisplayName("Fronteiras de prazo comercial: 1 dia -> 1 mês, 30 dias -> 1 mês, 31 dias -> 2 meses")
    void shouldCalculateCommercialTermBoundariesCorrectly(LocalDate dueDate, int expectedTermMonths) {
        SimulationResult result = simulationService.simulate(
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("1000.00"),
                dueDate,
                CurrencyCode.BRL
        );

        assertThat(result.termMonths()).isEqualTo(expectedTermMonths);
    }

    @Test
    @DisplayName("Rejeita dueDate igual a operationDate com InvalidDueDateException sem consultar câmbio")
    void shouldRejectDueDateEqualToOperationDate() {
        assertThatThrownBy(() -> simulationService.simulate(
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                OPERATION_DATE,
                CurrencyCode.USD
        ))
                .isInstanceOf(InvalidDueDateException.class)
                .hasMessageContaining("Due date (2026-11-04) must be strictly after operation date (2026-11-04)");

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("Rejeita dueDate anterior a operationDate com InvalidDueDateException sem consultar câmbio")
    void shouldRejectDueDateBeforeOperationDate() {
        LocalDate pastDate = OPERATION_DATE.minusDays(1);

        assertThatThrownBy(() -> simulationService.simulate(
                ReceivableType.CHEQUE_PRE_DATADO,
                new BigDecimal("25000.00"),
                pastDate,
                CurrencyCode.USD
        ))
                .isInstanceOf(InvalidDueDateException.class)
                .hasMessageContaining("Due date (2026-11-03) must be strictly after operation date (2026-11-04)");

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("Propaga ExchangeRateUnavailableException quando paymentCurrency é USD e não há cotação vigente")
    void shouldPropagateExchangeRateUnavailableExceptionWhenUsdRateIsMissing() {
        LocalDate dueDate = LocalDate.of(2027, 2, 2);
        when(exchangeRateService.getExchangeRate(CurrencyCode.USD, FIXED_INSTANT))
                .thenThrow(new ExchangeRateUnavailableException(CurrencyCode.USD, FIXED_INSTANT));

        assertThatThrownBy(() -> simulationService.simulate(
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                dueDate,
                CurrencyCode.USD
        ))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    @DisplayName("Captura clock.instant() uma única vez por simulação e reutiliza o mesmo simulatedAt")
    void shouldCaptureSingleInstantFromClockAndReuseForExchangeRateLookup() {
        Clock advancingClock = mock(Clock.class);
        AtomicInteger callCount = new AtomicInteger(0);
        when(advancingClock.instant()).thenAnswer(invocation -> {
            if (callCount.incrementAndGet() == 1) {
                return FIXED_INSTANT;
            }
            return FIXED_INSTANT.plusSeconds(60);
        });

        SimulationService serviceWithAdvancingClock = new SimulationService(
                new PricingEngine(),
                new CommercialTermCalculator(),
                new CurrencyConverter(),
                exchangeRateService,
                advancingClock,
                BASE_RATE_MONTHLY
        );

        Instant effectiveAt = Instant.parse("2026-11-04T12:00:00Z");
        ExchangeRate usdRate = new ExchangeRate(
                CurrencyCode.USD,
                new BigDecimal("5.432100"),
                effectiveAt,
                effectiveAt
        );
        when(exchangeRateService.getExchangeRate(CurrencyCode.USD, FIXED_INSTANT)).thenReturn(usdRate);

        SimulationResult result = serviceWithAdvancingClock.simulate(
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2),
                CurrencyCode.USD
        );

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(result.simulatedAt()).isEqualTo(FIXED_INSTANT);
        verify(exchangeRateService).getExchangeRate(CurrencyCode.USD, FIXED_INSTANT);
    }
}
