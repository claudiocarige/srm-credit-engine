package com.claudiocarige.backendcreditengine.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claudiocarige.backendcreditengine.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SimulationIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:25:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        jdbcTemplate.execute("DELETE FROM exchange_rates");
    }

    @Test
    @DisplayName("Fluxo integrado completo dos equivalentes HTTP aos Golden Cases C1, C2 e C3")
    void shouldExecuteGoldenCasesC1C2AndC3EquivalentsEndToEnd() throws Exception {
        // 1. Cadastra cotação USD vigente via POST /api/v1/exchange-rates
        String rateJson = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-04T12:00:00Z"
                }
                """;
        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rateJson))
                .andExpect(status().isCreated());

        // 2. Equivalente HTTP ao Golden Case C1 (Duplicata Mercantil R$ 100.000,00, 90 dias -> 3 meses, BRL)
        String c1Json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "BRL"
                }
                """;
        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(c1Json))
                .andExpect(status().isOk())
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

        // 3. Equivalente HTTP ao Golden Case C2 (Cheque Pré-datado R$ 25.000,00, 60 dias -> 2 meses, BRL)
        String c2Json = """
                {
                    "type": "CHEQUE_PRE_DATADO",
                    "faceValueBrl": "25000.00",
                    "dueDate": "2027-01-03",
                    "paymentCurrency": "BRL"
                }
                """;
        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(c2Json))
                .andExpect(status().isOk())
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

        // 4. Equivalente HTTP ao Golden Case C3 (Duplicata Mercantil R$ 100.000,00, 90 dias -> 3 meses, USD)
        String c3Json = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "USD"
                }
                """;
        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(c3Json))
                .andExpect(status().isOk())
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
    @DisplayName("Comprovação de ausência de efeitos persistentes: simulações BRL e USD não alteram nenhuma tabela")
    void shouldHaveZeroPersistentSideEffectsAcrossAllFinancialTables() throws Exception {
        // Preparação ANTES de aferir o estado inicial do banco
        String rateJson = """
                {
                    "currency": "USD",
                    "rateBrl": "5.432100",
                    "effectiveAt": "2026-11-04T12:00:00Z"
                }
                """;
        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rateJson))
                .andExpect(status().isCreated());

        // Captura o estado inicial das tabelas financeiras após a preparação
        Map<String, Integer> countsBefore = snapshotTableCounts();
        Map<String, Object> usdRateRowBefore = jdbcTemplate.queryForMap(
                "SELECT id, currency, rate_brl, effective_at, created_at FROM exchange_rates"
        );

        // Executa simulações em BRL e USD
        String brlSimulationJson = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "BRL"
                }
                """;
        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brlSimulationJson))
                .andExpect(status().isOk());

        String usdSimulationJson = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "USD"
                }
                """;
        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usdSimulationJson))
                .andExpect(status().isOk());

        // Aferição pós-simulação: contagens e registro existente inalterados
        Map<String, Integer> countsAfter = snapshotTableCounts();
        Map<String, Object> usdRateRowAfter = jdbcTemplate.queryForMap(
                "SELECT id, currency, rate_brl, effective_at, created_at FROM exchange_rates"
        );

        assertThat(countsAfter).isEqualTo(countsBefore);
        assertThat(usdRateRowAfter).isEqualTo(usdRateRowBefore);
    }

    @Test
    @DisplayName("Retorna 422 EXCHANGE_RATE_UNAVAILABLE quando paymentCurrency é USD e não há cotação vigente")
    void shouldReturn422ExchangeRateUnavailableWhenUsdRateMissingOrFutureOnly() throws Exception {
        // Cadastra apenas cotação futura (posterior a FIXED_INSTANT)
        String futureRateJson = """
                {
                    "currency": "USD",
                    "rateBrl": "5.500000",
                    "effectiveAt": "2026-11-04T18:00:00Z"
                }
                """;
        mockMvc.perform(post("/api/v1/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(futureRateJson))
                .andExpect(status().isCreated());

        String usdSimulationJson = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "paymentCurrency": "USD"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usdSimulationJson))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("EXCHANGE_RATE_UNAVAILABLE")));
    }

    @Test
    @DisplayName("Retorna 422 INVALID_DUE_DATE quando dueDate <= operationDate")
    void shouldReturn422InvalidDueDateWhenDueDateIsOnOrBeforeOperationDate() throws Exception {
        String invalidDueDateJson = """
                {
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2026-11-04",
                    "paymentCurrency": "BRL"
                }
                """;

        mockMvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidDueDateJson))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("INVALID_DUE_DATE")));
    }

    @Test
    @DisplayName("OpenAPI e Swagger UI: contrato oficial acessível, Swagger UI carrega YAML oficial sem Petstore e /v3/api-docs desabilitado")
    void shouldServeOfficialOpenApiYamlAndConfigureSwaggerUiWithoutDynamicSpecOrPetstore() throws Exception {
        // 1. Contrato oficial acessível por HTTP
        mockMvc.perform(get("/openapi/srm-credit-engine.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi: 3.0.3")))
                .andExpect(content().string(containsString("/api/v1/simulations:")));

        // 2. Swagger UI HTML funcional
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));

        // 3. Carregamento efetivo do YAML oficial pelo inicializador do Swagger UI e ausência do Petstore padrão
        mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/openapi/srm-credit-engine.yaml")))
                .andExpect(content().string(not(containsString("petstore.swagger.io"))));

        // 4. Ausência de especificação dinâmica concorrente (/v3/api-docs desabilitado)
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    private Map<String, Integer> snapshotTableCounts() {
        List<String> candidateTables = List.of(
                "receivables",
                "exchange_rates",
                "assignors",
                "settlements",
                "settlement_items"
        );
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : candidateTables) {
            String regClass = jdbcTemplate.queryForObject(
                    "SELECT to_regclass('public.' || ?)",
                    String.class,
                    table
            );
            if (regClass != null) {
                Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
                counts.put(table, count);
            }
        }
        assertThat(counts).containsKeys("receivables", "exchange_rates", "assignors");
        return counts;
    }
}
