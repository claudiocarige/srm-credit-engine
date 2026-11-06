package com.claudiocarige.backendcreditengine.exchangerate.web;

import com.claudiocarige.backendcreditengine.common.web.GlobalExceptionHandler;
import com.claudiocarige.backendcreditengine.exchangerate.application.ExchangeRateService;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.DuplicateExchangeRateException;
import com.claudiocarige.backendcreditengine.exchangerate.application.exception.ExchangeRateUnavailableException;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExchangeRateController.class)
@Import(GlobalExceptionHandler.class)
class ExchangeRateControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    @MockitoBean
    private Clock clock;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_NOW);
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: returns 201 Created conforming to OpenAPI schema without Location header")
    void shouldCreateExchangeRateSuccessfully() throws Exception {
        BigDecimal rate = new BigDecimal("5.432100");
        Instant effectiveAt = Instant.parse("2026-11-06T10:00:00Z");
        ExchangeRate saved = new ExchangeRate(CurrencyCode.USD, rate, effectiveAt, FIXED_NOW);

        when(exchangeRateService.registerExchangeRate(eq(CurrencyCode.USD), eq(rate), eq(effectiveAt)))
                .thenReturn(saved);

        String json = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-06T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.rateBrl", is("5.432100")))
                .andExpect(jsonPath("$.effectiveAt", is("2026-11-06T10:00:00Z")));
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: rejects unknown JSON properties with 400 VALIDATION_ERROR")
    void shouldRejectUnknownPropertiesInPostRequest() throws Exception {
        String json = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-06T10:00:00Z",
                    "unexpectedField": "malicious"
                }
                """;

mockMvc.perform(post("/api/v1/exchange-rates")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: rejects numeric rateBrl with 400 VALIDATION_ERROR")
    void shouldRejectNumericRateBrlInPostRequest() throws Exception {
        String json = """
                {
                    "currency": "USD",
                    "rateBrl": 5.432100,
                    "effectiveAt": "2026-11-06T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: rejects invalid rateBrl format with 400 VALIDATION_ERROR")
    void shouldRejectInvalidRateBrlFormatInPostRequest() throws Exception {
        String json = """
                {
                    "currency": "USD",
                    "rateBrl": "0.000000",
                    "effectiveAt": "2026-11-06T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: rejects BRL currency with 400 VALIDATION_ERROR and field 'currency'")
    void shouldRejectNonUsdCurrencyInPostRequest() throws Exception {
        String json = """
                {
                    "currency": "BRL",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-06T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("currency")));

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: rejects sub-microsecond effectiveAt with 400 VALIDATION_ERROR and field 'effectiveAt'")
    void shouldRejectSubMicrosecondEffectiveAtInPostRequest() throws Exception {
        String json = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-06T10:00:00.123456789Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("effectiveAt")));

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("POST /api/v1/exchange-rates: translates DuplicateExchangeRateException to 409 Conflict")
    void shouldReturn409WhenDuplicateExchangeRate() throws Exception {
        Instant effectiveAt = Instant.parse("2026-11-06T10:00:00Z");

        when(exchangeRateService.registerExchangeRate(any(), any(), any()))
                .thenThrow(new DuplicateExchangeRateException(CurrencyCode.USD, effectiveAt));

        String json = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-06T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT")));
    }

    @Test
    @DisplayName("GET /api/v1/exchange-rates/current: returns 200 OK with rate formatted with 6 decimals")
    void shouldReturn200WithCurrentExchangeRate() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);
        ExchangeRate rate = new ExchangeRate(
                CurrencyCode.USD,
                new BigDecimal("5.432100"),
                FIXED_NOW.minusSeconds(100),
                FIXED_NOW
        );
        when(exchangeRateService.getExchangeRate(CurrencyCode.USD, FIXED_NOW)).thenReturn(rate);

        mockMvc.perform(get("/api/v1/exchange-rates/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.rateBrl", is("5.432100")));
    }

    @Test
    @DisplayName("GET /api/v1/exchange-rates/current: returns 422 Unprocessable Entity when no rate is available")
    void shouldReturn422WhenNoCurrentExchangeRateAvailable() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(exchangeRateService.getExchangeRate(CurrencyCode.USD, FIXED_NOW))
                .thenThrow(new ExchangeRateUnavailableException(CurrencyCode.USD, FIXED_NOW));

        mockMvc.perform(get("/api/v1/exchange-rates/current"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("EXCHANGE_RATE_UNAVAILABLE")));
    }

    @Test
    @DisplayName("GET /api/v1/exchange-rates/current?currency=BRL: rejects BRL with 400 VALIDATION_ERROR before querying service")
    void shouldReturn400WhenCurrencyIsBrlInCurrentEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/exchange-rates/current?currency=BRL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("currency")));

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("GET /api/v1/exchange-rates/current?currency=EUR: rejects EUR with 400 VALIDATION_ERROR before querying service")
    void shouldReturn400WhenCurrencyIsEurInCurrentEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/exchange-rates/current?currency=EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("currency")));

        verifyNoInteractions(exchangeRateService);
    }
}