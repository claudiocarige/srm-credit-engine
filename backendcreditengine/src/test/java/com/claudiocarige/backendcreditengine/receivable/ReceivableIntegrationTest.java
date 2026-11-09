package com.claudiocarige.backendcreditengine.receivable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claudiocarige.backendcreditengine.TestcontainersConfiguration;
import com.claudiocarige.backendcreditengine.receivable.persistence.ReceivableRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReceivableIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:20:00Z");
    private static final UUID ASSIGNOR_ALFA_ID = UUID.fromString("8f14e45f-ceea-467a-9a38-1b4c9f8b0001");
    private static final UUID ASSIGNOR_BETA_ID = UUID.fromString("9a25f560-dffb-478b-8b49-2c5da09c0002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Environment environment;

    @MockitoSpyBean
    private ReceivableRepository receivableRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        cleanAndRestoreSeedAssignors();
    }

    @AfterEach
    void tearDown() {
        reset(receivableRepository);
        cleanAndRestoreSeedAssignors();
    }

    private void cleanAndRestoreSeedAssignors() {
        jdbcTemplate.execute("DELETE FROM receivables");
        jdbcTemplate.execute("DELETE FROM assignors");
        jdbcTemplate.execute("""
                INSERT INTO assignors (id, document, name, created_at)
                VALUES
                    ('8f14e45f-ceea-467a-9a38-1b4c9f8b0001', '12345678000190', 'Industria Alfa Ltda', '2026-11-04T00:00:00Z'),
                    ('9a25f560-dffb-478b-8b49-2c5da09c0002', '98765432000110', 'Comercio Beta S.A.', '2026-11-04T00:00:00Z')
                ON CONFLICT (id) DO NOTHING
                """);
    }

    @Test
    @DisplayName("GET /api/v1/assignors: Retorna cedentes do seed V3 ordenados alfabeticamente por nome")
    void shouldListSeedAssignorsOrderedByName() throws Exception {
        mockMvc.perform(get("/api/v1/assignors")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(ASSIGNOR_BETA_ID.toString())))
                .andExpect(jsonPath("$[0].document", is("98765432000110")))
                .andExpect(jsonPath("$[0].name", is("Comercio Beta S.A.")))
                .andExpect(jsonPath("$[1].id", is(ASSIGNOR_ALFA_ID.toString())))
                .andExpect(jsonPath("$[1].document", is("12345678000190")))
                .andExpect(jsonPath("$[1].name", is("Industria Alfa Ltda")));
    }

    @Test
    @DisplayName("Round-trip POST /api/v1/receivables -> Location -> GET /api/v1/receivables/{id} com open-in-view=false")
    void shouldCreateReceivableAndRetrieveViaLocationHeader() throws Exception {
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");

        String createJson = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "  dup-2026-0042  ",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """;

        MvcResult postResult = mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.assignorId", is(ASSIGNOR_ALFA_ID.toString())))
                .andExpect(jsonPath("$.documentNumber", is("DUP-2026-0042")))
                .andExpect(jsonPath("$.type", is("DUPLICATA_MERCANTIL")))
                .andExpect(jsonPath("$.faceValueBrl", is("100000.00")))
                .andExpect(jsonPath("$.dueDate", is("2027-02-02")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.createdAt", is("2026-11-04T15:20:00Z")))
                .andReturn();

        String location = postResult.getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/v1/receivables/");

        mockMvc.perform(get(location)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignorId", is(ASSIGNOR_ALFA_ID.toString())))
                .andExpect(jsonPath("$.documentNumber", is("DUP-2026-0042")))
                .andExpect(jsonPath("$.type", is("DUPLICATA_MERCANTIL")))
                .andExpect(jsonPath("$.faceValueBrl", is("100000.00")))
                .andExpect(jsonPath("$.dueDate", is("2027-02-02")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.createdAt", is("2026-11-04T15:20:00Z")));
    }

    @Test
    @DisplayName("Unicidade sequencial: 409 para mesmo (assignorId, documentNumber normalizado) e 201 para cedente distinto")
    void shouldEnforceUniquenessPerAssignorAndAllowSameDocumentForDifferentAssignor() throws Exception {
        String firstCreate = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "DUP-2026-0042",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstCreate))
                .andExpect(status().isCreated());

        String duplicateForSameAssignor = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "  dup-2026-0042  ",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "50000.00",
                    "dueDate": "2027-03-01"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateForSameAssignor))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_RECEIVABLE_DOCUMENT")));

        String sameDocForSecondAssignor = """
                {
                    "assignorId": "9a25f560-dffb-478b-8b49-2c5da09c0002",
                    "documentNumber": "DUP-2026-0042",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sameDocForSecondAssignor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignorId", is(ASSIGNOR_BETA_ID.toString())))
                .andExpect(jsonPath("$.documentNumber", is("DUP-2026-0042")));
    }

    @Test
    @DisplayName("Concorrência real com PostgreSQL: duas requisições simultâneas resultam em 1 HTTP 201, 1 HTTP 409, 0 HTTP 500 e 1 registro no banco")
    void shouldHandleConcurrentDuplicateReceivableCreationGracefully() throws Exception {
        CyclicBarrier preCheckBarrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            preCheckBarrier.await(5, TimeUnit.SECONDS);
            return false;
        }).when(receivableRepository).existsByAssignor_IdAndDocumentNumber(ASSIGNOR_ALFA_ID, "DUP-CONC-99");

        String payloadThread1 = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "  dup-conc-99  ",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """;

        String payloadThread2 = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "DUP-CONC-99",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """;

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<MvcResult> task1 = () -> {
                readyLatch.countDown();
                assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/v1/receivables")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payloadThread1))
                        .andReturn();
            };

            Callable<MvcResult> task2 = () -> {
                readyLatch.countDown();
                assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post("/api/v1/receivables")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payloadThread2))
                        .andReturn();
            };

            Future<MvcResult> future1 = executor.submit(task1);
            Future<MvcResult> future2 = executor.submit(task2);

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            MvcResult result1 = future1.get(10, TimeUnit.SECONDS);
            MvcResult result2 = future2.get(10, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(
                    result1.getResponse().getStatus(),
                    result2.getResponse().getStatus()
            );

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);

            MvcResult conflictResult = result1.getResponse().getStatus() == 409 ? result1 : result2;
            assertThat(conflictResult.getResponse().getContentAsString())
                    .contains("\"errorCode\":\"DUPLICATE_RECEIVABLE_DOCUMENT\"");

            Integer persistedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM receivables WHERE assignor_id = ? AND document_number = ?",
                    Integer.class,
                    ASSIGNOR_ALFA_ID,
                    "DUP-CONC-99"
            );
            assertThat(persistedCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Semântica completa de GET /api/v1/receivables: lista vazia, default OPEN, filtro SETTLED, ordenação dueDate ASC, documentNumber ASC e 404 para cedente inexistente")
    void shouldFulfillListingSemanticsAndOrdering() throws Exception {
        // 1. Cedente existente sem títulos no status solicitado -> 200 OK com lista vazia []
        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ALFA_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 2. Cadastro de 3 recebíveis OPEN fora de ordem para validar ordenação dueDate ASC, documentNumber ASC
        createOpenReceivable(ASSIGNOR_ALFA_ID, "DUP-2026-0020", "2027-02-02", "50000.00");
        createOpenReceivable(ASSIGNOR_ALFA_ID, "DUP-2026-0010", "2027-02-02", "60000.00");
        createOpenReceivable(ASSIGNOR_ALFA_ID, "DUP-2026-0099", "2027-01-15", "25000.00");

        // 3. Inserção direta via SQL de 1 título SETTLED (sem executar liquidação)
        jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-SETTLED-01', 'DUPLICATA_MERCANTIL', 80000.00, '2026-12-20', 'SETTLED', 0, '2026-11-04T15:20:00Z')
                """, UUID.randomUUID(), ASSIGNOR_ALFA_ID);

        // 4. Filtro status omitido -> aplica OPEN e ordena por dueDate ASC, documentNumber ASC
        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ALFA_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].documentNumber", is("DUP-2026-0099")))
                .andExpect(jsonPath("$[0].dueDate", is("2027-01-15")))
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[1].documentNumber", is("DUP-2026-0010")))
                .andExpect(jsonPath("$[1].dueDate", is("2027-02-02")))
                .andExpect(jsonPath("$[2].documentNumber", is("DUP-2026-0020")))
                .andExpect(jsonPath("$[2].dueDate", is("2027-02-02")));

        // 5. Filtro status=SETTLED -> retorna apenas o título SETTLED para Alfa, e [] para Beta
        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ALFA_ID.toString())
                        .param("status", "SETTLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].documentNumber", is("DUP-SETTLED-01")))
                .andExpect(jsonPath("$[0].status", is("SETTLED")));

        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_BETA_ID.toString())
                        .param("status", "SETTLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 6. Cedente inexistente -> 404 ASSIGNOR_NOT_FOUND
        UUID nonExistentAssignor = UUID.fromString("00000000-0000-0000-0000-000000000099");
        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", nonExistentAssignor.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("ASSIGNOR_NOT_FOUND")));
    }

    @Test
    @DisplayName("Verificação empírica de ausência de N+1 e funcionamento com open-in-view=false na listagem de múltiplos recebíveis")
    void shouldNotTriggerNPlusOneQueriesWhenListingMultipleReceivables() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createOpenReceivable(
                    ASSIGNOR_ALFA_ID,
                    "DUP-N1-000" + i,
                    "2027-02-0" + i,
                    "10000.00"
            );
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<String> expectedDocs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            expectedDocs.add("DUP-N1-000" + i);
        }

        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ALFA_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].documentNumber", is(expectedDocs.get(0))))
                .andExpect(jsonPath("$[4].documentNumber", is(expectedDocs.get(4))));

        // Com @EntityGraph(attributePaths = "assignor"), executa exatamente 2 statements SQL:
        // 1) existsById em assignors e 2) SELECT r JOIN assignors para todos os 5 recebíveis (constante, sem N+1).
        long statementCount = statistics.getPrepareStatementCount();
        assertThat(statementCount).isLessThanOrEqualTo(2L);
    }

    private void createOpenReceivable(UUID assignorId, String documentNumber, String dueDate, String faceValueBrl) throws Exception {
        String json = """
                {
                    "assignorId": "%s",
                    "documentNumber": "%s",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "%s",
                    "dueDate": "%s"
                }
                """.formatted(assignorId, documentNumber, faceValueBrl, dueDate);

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }
}
