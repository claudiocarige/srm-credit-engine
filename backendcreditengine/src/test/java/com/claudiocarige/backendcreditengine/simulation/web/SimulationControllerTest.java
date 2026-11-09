package com.claudiocarige.backendcreditengine.simulation.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claudiocarige.backendcreditengine.common.web.GlobalExceptionHandler;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.ExchangeRateUnavailableException;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.simulation.application.SimulationResult;
import com.claudiocarige.backendcreditengine.simulation.application.SimulationService;
import com.claudiocarige.backendcreditengine.simulation.application.exception.InvalidDueDateException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SimulationController.class)
@Import(GlobalExceptionHandler.class)
class SimulationControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:25:00Z");
    private static final LocalDate OPERATION_DATE = LocalDate.of(2026, 11, 4);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
    }

    @Test
    @DisplayName("POST /api/v1/simulations: Equivalente HTTP ao Golden Case C1 retorna 200 OK com os 14 campos do contrato")
    void shouldSimulateGoldenCaseC1EquivalentSuccessfully() throws Exception {
        LocalDate dueDate = LocalDate.of(2027, 2, 2);
        SimulationResult result = new SimulationResult(
                FIXED_INSTANT,
                OPERATION_DATE,
                ReceivableType.DUPLICATA_MERCANTIL,
                dueDate,
                3,
                new BigDecimal("100000.00"),
                new BigDecimal("0.010000"),
                new BigDecimal("0.015000"),
                new BigDecimal("92859.94"),
                new BigDecimal("7140.06"),
                CurrencyCode.BRL,
                null,
                null,
                new BigDecimal("92859.94")
        );

        when(simulationService.simulate(
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(dueDate),
                eq(CurrencyCode.BRL)
        )).thenReturn(result);

        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "BRL"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.simulatedAt", is("2026-11-04T15:25:00Z")))
                .andExpect(jsonPath("$.operationDate", is("2026-11-04")))
                .andExpect(jsonPath("$.type", is("DUPLICATA_MERCANTIL")))
                .andExpect(jsonPath("$.dueDate", is("2027-02-02")))
                .andExpect(jsonPath("$.termMonths", is(3)))
                .andExpect(jsonPath("$.faceValueBrl", is("100000.00")))
                .andExpect(jsonPath("$.baseRateMonthly", is("0.010000")))
                .andExpect(jsonPath("$.spreadMonthly", is("0.015000")))
                .andExpect(jsonPath("$.presentValueBrl", is("92859.94")))
                .andExpect(jsonPath("$.discountBrl", is("7140.06")))
                .andExpect(jsonPath("$.paymentCurrency", is("BRL")))
                .andExpect(jsonPath("$.exchangeRateApplied").value(nullValue()))
                .andExpect(jsonPath("$.exchangeRateEffectiveAt").value(nullValue()))
                .andExpect(jsonPath("$.paidAmount", is("92859.94")));
    }

    @Test
    @DisplayName("POST /api/v1/simulations: Equivalente HTTP ao Golden Case C2 retorna 200 OK com os 14 campos do contrato")
    void shouldSimulateGoldenCaseC2EquivalentSuccessfully() throws Exception {
        LocalDate dueDate = LocalDate.of(2027, 1, 3);
        SimulationResult result = new SimulationResult(
                FIXED_INSTANT,
                OPERATION_DATE,
                ReceivableType.CHEQUE_PRE_DATADO,
                dueDate,
                2,
                new BigDecimal("25000.00"),
                new BigDecimal("0.010000"),
                new BigDecimal("0.025000"),
                new BigDecimal("23337.77"),
                new BigDecimal("1662.23"),
                CurrencyCode.BRL,
                null,
                null,
                new BigDecimal("23337.77")
        );

        when(simulationService.simulate(
                eq(ReceivableType.CHEQUE_PRE_DATADO),
                eq(new BigDecimal("25000.00")),
                eq(dueDate),
                eq(CurrencyCode.BRL)
        )).thenReturn(result);

        String json = """
                {
                    "type": "CHEQUE_PRE_DATADO",
                    "faceValueBrl": "25000.00",
                    "dueDate": "2027-01-03",
                    "paymentCurrency": "BRL"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.simulatedAt", is("2026-11-04T15:25:00Z")))
                .andExpect(jsonPath("$.operationDate", is("2026-11-04")))
                .andExpect(jsonPath("$.type", is("CHEQUE_PRE_DATADO")))
                .andExpect(jsonPath("$.dueDate", is("2027-01-03")))
                .andExpect(jsonPath("$.termMonths", is(2)))
                .andExpect(jsonPath("$.faceValueBrl", is("25000.00")))
                .andExpect(jsonPath("$.baseRateMonthly", is("0.010000")))
                .andExpect(jsonPath("$.spreadMonthly", is("0.025000")))
                .andExpect(jsonPath("$.presentValueBrl", is("23337.77")))
                .andExpect(jsonPath("$.discountBrl", is("1662.23")))
                .andExpect(jsonPath("$.paymentCurrency", is("BRL")))
                .andExpect(jsonPath("$.exchangeRateApplied").value(nullValue()))
                .andExpect(jsonPath("$.exchangeRateEffectiveAt").value(nullValue()))
                .andExpect(jsonPath("$.paidAmount", is("23337.77")));
    }

    @Test
    @DisplayName("POST /api/v1/simulations: Equivalente HTTP ao Golden Case C3 retorna 200 OK com os 14 campos do contrato")
    void shouldSimulateGoldenCaseC3EquivalentSuccessfully() throws Exception {
        LocalDate dueDate = LocalDate.of(2027, 2, 2);
        Instant effectiveAt = Instant.parse("2026-11-04T12:00:00Z");
        SimulationResult result = new SimulationResult(
                FIXED_INSTANT,
                OPERATION_DATE,
                ReceivableType.DUPLICATA_MERCANTIL,
                dueDate,
                3,
                new BigDecimal("100000.00"),
                new BigDecimal("0.010000"),
                new BigDecimal("0.015000"),
                new BigDecimal("92859.94"),
                new BigDecimal("7140.06"),
                CurrencyCode.USD,
                new BigDecimal("5.432100"),
                effectiveAt,
                new BigDecimal("17094.67")
        );

        when(simulationService.simulate(
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(dueDate),
                eq(CurrencyCode.USD)
        )).thenReturn(result);

        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "USD"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.simulatedAt", is("2026-11-04T15:25:00Z")))
                .andExpect(jsonPath("$.operationDate", is("2026-11-04")))
                .andExpect(jsonPath("$.type", is("DUPLICATA_MERCANTIL")))
                .andExpect(jsonPath("$.dueDate", is("2027-02-02")))
                .andExpect(jsonPath("$.termMonths", is(3)))
                .andExpect(jsonPath("$.faceValueBrl", is("100000.00")))
                .andExpect(jsonPath("$.baseRateMonthly", is("0.010000")))
                .andExpect(jsonPath("$.spreadMonthly", is("0.015000")))
                .andExpect(jsonPath("$.presentValueBrl", is("92859.94")))
                .andExpect(jsonPath("$.discountBrl", is("7140.06")))
                .andExpect(jsonPath("$.paymentCurrency", is("USD")))
                .andExpect(jsonPath("$.exchangeRateApplied", is("5.432100")))
                .andExpect(jsonPath("$.exchangeRateEffectiveAt", is("2026-11-04T12:00:00Z")))
                .andExpect(jsonPath("$.paidAmount", is("17094.67")));
    }

    @Test
    @DisplayName("POST /api/v1/simulations: rejeita propriedade JSON desconhecida com 400 VALIDATION_ERROR")
    void shouldRejectUnknownJsonProperty() throws Exception {
        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "BRL",
                    "manualDiscount": "10.00"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.instance", is("/api/v1/simulations")));

        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("POST /api/v1/simulations: rejeita faceValueBrl numérico sem aspas com 400 VALIDATION_ERROR")
    void shouldRejectUnquotedNumericFaceValueBrl() throws Exception {
        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": 100000.00,
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "BRL"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(simulationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-50.00", "100.123", "1000000000.00", "abc"})
    @DisplayName("POST /api/v1/simulations: rejeita faceValueBrl inválido com 400 VALIDATION_ERROR e fieldErrors")
    void shouldRejectInvalidFaceValueBrlPattern(String invalidFaceValue) throws Exception {
        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "%s",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "BRL"
                }
                """.formatted(invalidFaceValue);

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("faceValueBrl")));

        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("POST /api/v1/simulations: rejeita campos obrigatórios ausentes com 400 VALIDATION_ERROR e fieldErrors")
    void shouldRejectMissingRequiredFields() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("type")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("faceValueBrl")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("dueDate")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("paymentCurrency")));

        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("POST /api/v1/simulations: traduz InvalidDueDateException para 422 INVALID_DUE_DATE")
    void shouldTranslateInvalidDueDateExceptionTo422() throws Exception {
        when(simulationService.simulate(
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(OPERATION_DATE),
                eq(CurrencyCode.BRL)
        )).thenThrow(new InvalidDueDateException(OPERATION_DATE, OPERATION_DATE));

        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2026-11-04",
                    "paymentCurrency": "BRL"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.title", is("Unprocessable Entity")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_DUE_DATE")))
                .andExpect(jsonPath("$.instance", is("/api/v1/simulations")))
                .andExpect(jsonPath("$.timestamp", is("2026-11-04T15:25:00Z")));
    }

    @Test
    @DisplayName("POST /api/v1/simulations: traduz ExchangeRateUnavailableException para 422 EXCHANGE_RATE_UNAVAILABLE")
    void shouldTranslateExchangeRateUnavailableExceptionTo422() throws Exception {
        LocalDate dueDate = LocalDate.of(2027, 2, 2);
        when(simulationService.simulate(
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(dueDate),
                eq(CurrencyCode.USD)
        )).thenThrow(new ExchangeRateUnavailableException(CurrencyCode.USD, FIXED_INSTANT));

        String json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "USD"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.title", is("Unprocessable Entity")))
                .andExpect(jsonPath("$.errorCode", is("EXCHANGE_RATE_UNAVAILABLE")))
                .andExpect(jsonPath("$.instance", is("/api/v1/simulations")))
                .andExpect(jsonPath("$.timestamp", is("2026-11-04T15:25:00Z")));
    }
}
