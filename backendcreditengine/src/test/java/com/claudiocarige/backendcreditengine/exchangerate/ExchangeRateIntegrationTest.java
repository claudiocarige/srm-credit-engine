package com.claudiocarige.backendcreditengine.exchangerate;

import com.claudiocarige.backendcreditengine.TestcontainersConfiguration;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate;
import com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRateRepository;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ExchangeRateIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExchangeRateRepository repository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_NOW);
        jdbcTemplate.execute("DELETE FROM exchange_rates");
    }

    @Test
    @DisplayName("Round-trip completo HTTP POST persistindo no PostgreSQL real e recuperação via GET /current")
    void shouldPersistAndRetrieveExchangeRateViaHttpRoundTrip() throws Exception {
        String json = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-06T09:00:00.123456Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.rateBrl", is("5.432100")))
                .andExpect(jsonPath("$.effectiveAt", is("2026-11-06T09:00:00.123456Z")));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class);
        assertThat(count).isEqualTo(1);

        mockMvc.perform(get("/api/v1/exchange-rates/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.rateBrl", is("5.432100")));
    }

    @Test
    @DisplayName("Disparo real da constraint uk_exchange_rates_currency_effective_at retornando HTTP 409")
    void shouldReturn409OnDuplicateEffectiveAtInPostgres() throws Exception {
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
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT")));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Rejeição de effectiveAt com sub-microssegundos retornando HTTP 400 VALIDATION_ERROR")
    void shouldRejectSubMicrosecondPrecisionInHttp() throws Exception {
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

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Consulta HTTP GET recuperando cotação real ordenada por maior vigência <= instante atual")
    void shouldSelectHighestEffectiveAtBeforeCurrentInstant() throws Exception {
        Instant t1 = Instant.parse("2026-11-06T08:00:00Z");
        Instant t2 = Instant.parse("2026-11-06T09:00:00Z");
        Instant tFuture = Instant.parse("2026-11-06T15:00:00Z");

        repository.saveAndFlush(new ExchangeRate(CurrencyCode.USD, new BigDecimal("5.100000"), t1, FIXED_NOW));
        repository.saveAndFlush(new ExchangeRate(CurrencyCode.USD, new BigDecimal("5.200000"), t2, FIXED_NOW));
        repository.saveAndFlush(new ExchangeRate(CurrencyCode.USD, new BigDecimal("5.990000"), tFuture, FIXED_NOW));

        mockMvc.perform(get("/api/v1/exchange-rates/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateBrl", is("5.200000")))
                .andExpect(jsonPath("$.effectiveAt", is("2026-11-06T09:00:00Z")));
    }

    @Test
    @DisplayName("Preservação do histórico cambial em inserções retroativas sem sobrescrever registros prévios")
    void shouldPreserveHistoricalRatesWhenRetroactiveQuoteIsInserted() throws Exception {
        Instant tPast = Instant.parse("2026-11-05T12:00:00Z");
        Instant tFuture = Instant.parse("2026-11-06T12:00:00Z");

        repository.saveAndFlush(new ExchangeRate(CurrencyCode.USD, new BigDecimal("5.300000"), tFuture, FIXED_NOW));

        String jsonRetroactive = """
                {
                    "currency": "USD",
                    "rateBrl": "5.000000",
                    "effectiveAt": "2026-11-05T12:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRetroactive))
                .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class);
        assertThat(count).isEqualTo(2);

        Optional<ExchangeRate> queryPast = repository
                .findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                        CurrencyCode.USD,
                        Instant.parse("2026-11-05T15:00:00Z")
                );
        assertThat(queryPast).isPresent();
        assertThat(queryPast.get().getRateBrl()).isEqualByComparingTo("5.000000");

        Optional<ExchangeRate> queryLater = repository
                .findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                        CurrencyCode.USD,
                        Instant.parse("2026-11-06T15:00:00Z")
                );
        assertThat(queryLater).isPresent();
        assertThat(queryLater.get().getRateBrl()).isEqualByComparingTo("5.300000");
    }
}

