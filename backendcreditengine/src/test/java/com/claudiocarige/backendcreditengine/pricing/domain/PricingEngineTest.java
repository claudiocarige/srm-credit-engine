package com.claudiocarige.backendcreditengine.pricing.domain;

import com.claudiocarige.backendcreditengine.pricing.domain.calculation.CompoundInterestCalculator;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingInput;
import com.claudiocarige.backendcreditengine.pricing.domain.model.PricingResult;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.pricing.domain.strategy.PricingStrategy;
import com.claudiocarige.backendcreditengine.pricing.domain.strategy.TradeBillPricingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingEngineTest {

    private final PricingEngine engine = new PricingEngine();
    private final CompoundInterestCalculator calculator = new CompoundInterestCalculator();

    @Test
    @DisplayName("Golden Case C1: Deve precificar Duplicata Mercantil (3 meses, base 1%, spread 1,5%) exatamente ao centavo")
    void shouldCalculateGoldenCaseC1() {
        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            new BigDecimal("0.010000"),
            3
        );

        PricingResult result = engine.calculate(input);

        assertNotNull(result);
        assertEquals(ReceivableType.DUPLICATA_MERCANTIL, result.type());
        assertEquals(new BigDecimal("100000.00"), result.faceValueBrl());
        assertEquals(new BigDecimal("0.010000"), result.baseRateMonthly());
        assertEquals(new BigDecimal("0.015000"), result.spreadMonthly());
        assertEquals(3, result.termMonths());
        assertEquals(new BigDecimal("92859.94"), result.presentValueBrl());
        assertEquals(new BigDecimal("7140.06"), result.discountBrl());

        assertEquals(
            result.faceValueBrl(),
            result.presentValueBrl().add(result.discountBrl())
        );
    }

    @Test
    @DisplayName("Golden Case C2: Deve precificar Cheque Pré-datado (2 meses, base 1%, spread 2,5%) exatamente ao centavo")
    void shouldCalculateGoldenCaseC2() {
        PricingInput input = new PricingInput(
            ReceivableType.CHEQUE_PRE_DATADO,
            new BigDecimal("25000.00"),
            new BigDecimal("0.010000"),
            2
        );

        PricingResult result = engine.calculate(input);

        assertNotNull(result);
        assertEquals(ReceivableType.CHEQUE_PRE_DATADO, result.type());
        assertEquals(new BigDecimal("25000.00"), result.faceValueBrl());
        assertEquals(new BigDecimal("0.010000"), result.baseRateMonthly());
        assertEquals(new BigDecimal("0.025000"), result.spreadMonthly());
        assertEquals(2, result.termMonths());
        assertEquals(new BigDecimal("23337.77"), result.presentValueBrl());
        assertEquals(new BigDecimal("1662.23"), result.discountBrl());

        assertEquals(
            result.faceValueBrl(),
            result.presentValueBrl().add(result.discountBrl())
        );
    }

    @Test
    @DisplayName("Deve aceitar limites financeiros inferior (0.01) e superior (999999999.99)")
    void shouldAcceptBoundaryFaceValues() {
        PricingInput inputMin = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("0.01"),
            new BigDecimal("0.010000"),
            1
        );
        PricingResult resultMin = engine.calculate(inputMin);
        assertNotNull(resultMin);
        assertEquals(new BigDecimal("0.01"), resultMin.faceValueBrl());

        PricingInput inputMax = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("999999999.99"),
            new BigDecimal("0.010000"),
            1
        );
        PricingResult resultMax = engine.calculate(inputMax);
        assertNotNull(resultMax);
        assertEquals(new BigDecimal("999999999.99"), resultMax.faceValueBrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-100.00", "1000000000.00", "100.123"})
    @DisplayName("Deve rejeitar valor de face fora dos limites contratuais ou com escala maior que 2")
    void shouldRejectInvalidFaceValues(String invalidFaceValue) {
        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal(invalidFaceValue),
            new BigDecimal("0.010000"),
            1
        );

        assertThrows(PricingValidationException.class, () -> engine.calculate(input));
    }

    @Test
    @DisplayName("Deve rejeitar valor de face nulo")
    void shouldRejectNullFaceValue() {
        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            null,
            new BigDecimal("0.010000"),
            1
        );

        assertThrows(PricingValidationException.class, () -> engine.calculate(input));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -12})
    @DisplayName("Deve rejeitar prazo comercial menor que 1 mês")
    void shouldRejectInvalidTermMonths(int invalidTerm) {
        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("1000.00"),
            new BigDecimal("0.010000"),
            invalidTerm
        );

        assertThrows(PricingValidationException.class, () -> engine.calculate(input));
    }

    @Test
    @DisplayName("Deve rejeitar taxa base negativa, nula ou com escala excedendo 6 casas decimais")
    void shouldRejectInvalidBaseRates() {
        PricingInput inputNull = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("1000.00"),
            null,
            1
        );
        assertThrows(PricingValidationException.class, () -> engine.calculate(inputNull));

        PricingInput inputNegative = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("1000.00"),
            new BigDecimal("-0.010000"),
            1
        );
        assertThrows(PricingValidationException.class, () -> engine.calculate(inputNegative));

        PricingInput inputScale7 = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("1000.00"),
            new BigDecimal("0.0100001"),
            1
        );
        assertThrows(PricingValidationException.class, () -> engine.calculate(inputScale7));
    }

    @Test
    @DisplayName("Deve aceitar taxa base zero sem erro")
    void shouldAcceptZeroBaseRate() {
        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("1000.00"),
            new BigDecimal("0.000000"),
            1
        );

        PricingResult result = engine.calculate(input);
        assertNotNull(result);
        assertEquals(new BigDecimal("0.000000"), result.baseRateMonthly());
    }

    @Test
    @DisplayName("Deve rejeitar input nulo ou tipo de recebível nulo")
    void shouldRejectNullInputOrType() {
        assertThrows(PricingValidationException.class, () -> engine.calculate(null));

        PricingInput inputNullType = new PricingInput(
            null,
            new BigDecimal("1000.00"),
            new BigDecimal("0.010000"),
            1
        );
        assertThrows(PricingValidationException.class, () -> engine.calculate(inputNullType));
    }

    @Test
    @DisplayName("Deve validar o construtor do PricingEngine contra lista nula, vazia, com nulos ou tipos duplicados")
    void shouldValidateEngineConstructor() {
        assertThrows(PricingValidationException.class, () -> new PricingEngine(null));
        assertThrows(PricingValidationException.class, () -> new PricingEngine(List.of()));

        List<PricingStrategy> listWithNull = new ArrayList<>();
        listWithNull.add(new TradeBillPricingStrategy(calculator));
        listWithNull.add(null);
        assertThrows(PricingValidationException.class, () -> new PricingEngine(listWithNull));

        // Tipos duplicados no registro
        List<PricingStrategy> listDuplicates = List.of(
            new TradeBillPricingStrategy(calculator),
            new TradeBillPricingStrategy(calculator)
        );
        assertThrows(PricingValidationException.class, () -> new PricingEngine(listDuplicates));
    }

    @Test
    @DisplayName("Deve lançar exceção quando o motor não possuir strategy para o tipo solicitado")
    void shouldThrowWhenStrategyNotRegistered() {
        PricingEngine isolatedEngine = new PricingEngine(List.of(new TradeBillPricingStrategy(calculator)));

        PricingInput checkInput = new PricingInput(
            ReceivableType.CHEQUE_PRE_DATADO,
            new BigDecimal("1000.00"),
            new BigDecimal("0.010000"),
            1
        );

        PricingValidationException exception = assertThrows(
            PricingValidationException.class,
            () -> isolatedEngine.calculate(checkInput)
        );
        assertEquals("No pricing strategy registered for type: CHEQUE_PRE_DATADO", exception.getMessage());
    }

    @Test
    @DisplayName("Extensibilidade (OCP - Cenário A): Deve permitir registrar strategy com spread personalizado sem alterar o PricingEngine")
    void shouldDemonstrateExtensibilityWithCustomSpreadStrategy() {
        // Strategy fictícia com spread customizado de 3,00% a.m.
        PricingStrategy customSpreadStrategy = new PricingStrategy() {
            private final BigDecimal SPREAD = new BigDecimal("0.030000");

            @Override
            public ReceivableType getSupportedType() {
                return ReceivableType.DUPLICATA_MERCANTIL;
            }

            @Override
            public PricingResult calculate(PricingInput input) {
                BigDecimal totalRate = input.baseRateMonthly().add(SPREAD);
                BigDecimal presentValue = calculator.calculatePresentValue(
                    input.faceValueBrl(),
                    totalRate,
                    input.termMonths()
                );
                BigDecimal discount = input.faceValueBrl().subtract(presentValue);
                return new PricingResult(
                    getSupportedType(),
                    input.faceValueBrl(),
                    input.baseRateMonthly(),
                    SPREAD,
                    input.termMonths(),
                    presentValue,
                    discount
                );
            }
        };

        PricingEngine isolatedEngine = new PricingEngine(List.of(customSpreadStrategy));

        PricingInput input = new PricingInput(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("10000.00"),
            new BigDecimal("0.010000"),
            1
        );

        PricingResult result = isolatedEngine.calculate(input);

        // Taxa total = 1% + 3% = 4% -> VP = 10000 / 1.04 = 9615.3846... -> 9615.38 | Deságio = 384.62
        assertEquals(new BigDecimal("0.030000"), result.spreadMonthly());
        assertEquals(new BigDecimal("9615.38"), result.presentValueBrl());
        assertEquals(new BigDecimal("384.62"), result.discountBrl());
    }

    @Test
    @DisplayName("Extensibilidade (OCP - Cenário B): Deve permitir registrar strategy com fórmula alternativa (Desconto Simples) sem alterar o PricingEngine nem CompoundInterestCalculator")
    void shouldDemonstrateExtensibilityWithAlternativeFormulaStrategy() {
        // Strategy fictícia aplicando Desconto Simples Comercial: VP = VF * (1 - d * n)
        // d = baseRate + spread
        PricingStrategy simpleDiscountStrategy = new PricingStrategy() {
            private final BigDecimal SPREAD = new BigDecimal("0.020000");

            @Override
            public ReceivableType getSupportedType() {
                return ReceivableType.CHEQUE_PRE_DATADO;
            }

            @Override
            public PricingResult calculate(PricingInput input) {
                BigDecimal totalDiscountRate = input.baseRateMonthly().add(SPREAD);
                BigDecimal totalRateFactor = totalDiscountRate.multiply(BigDecimal.valueOf(input.termMonths()));
                BigDecimal discountFactor = BigDecimal.ONE.subtract(totalRateFactor);
                BigDecimal presentValue = input.faceValueBrl()
                    .multiply(discountFactor)
                    .setScale(2, RoundingMode.HALF_EVEN);
                BigDecimal discount = input.faceValueBrl().subtract(presentValue);

                return new PricingResult(
                    getSupportedType(),
                    input.faceValueBrl(),
                    input.baseRateMonthly(),
                    SPREAD,
                    input.termMonths(),
                    presentValue,
                    discount
                );
            }
        };

        PricingEngine isolatedEngine = new PricingEngine(List.of(simpleDiscountStrategy));

        // VF = 10000.00, base = 1%, spread = 2% -> d = 3% ao mês, n = 2 meses
        // Desconto total = 6% -> VP = 10000 * (1 - 0.06) = 9400.00 | Deságio = 600.00
        PricingInput input = new PricingInput(
            ReceivableType.CHEQUE_PRE_DATADO,
            new BigDecimal("10000.00"),
            new BigDecimal("0.010000"),
            2
        );

        PricingResult result = isolatedEngine.calculate(input);

        assertEquals(new BigDecimal("9400.00"), result.presentValueBrl());
        assertEquals(new BigDecimal("600.00"), result.discountBrl());
    }
}
