package com.claudiocarige.backendcreditengine.receivable.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claudiocarige.backendcreditengine.assignor.application.exception.AssignorNotFoundException;
import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.common.exception.InvalidDueDateException;
import com.claudiocarige.backendcreditengine.common.web.GlobalExceptionHandler;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.application.ReceivableService;
import com.claudiocarige.backendcreditengine.receivable.application.exception.DuplicateReceivableDocumentException;
import com.claudiocarige.backendcreditengine.receivable.application.exception.ReceivableNotFoundException;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import com.claudiocarige.backendcreditengine.receivable.persistence.Receivable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReceivableController.class)
@Import(GlobalExceptionHandler.class)
class ReceivableControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:20:00Z");
    private static final UUID ASSIGNOR_ID = UUID.fromString("8f14e45f-ceea-467a-9a38-1b4c9f8b0001");
    private static final UUID RECEIVABLE_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceivableService receivableService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Retorna 201 Created com header Location e body ReceivableResponse")
    void shouldCreateReceivableSuccessfully() throws Exception {
        Receivable created = createSampleReceivable(RECEIVABLE_ID, ASSIGNOR_ID, "DUP-2026-0042", ReceivableStatus.OPEN);

        when(receivableService.createReceivable(
                eq(ASSIGNOR_ID),
                eq("DUP-2026-0042"),
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(LocalDate.of(2027, 2, 2))
        )).thenReturn(created);

        String json = """
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
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/receivables/" + RECEIVABLE_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(RECEIVABLE_ID.toString())))
                .andExpect(jsonPath("$.assignorId", is(ASSIGNOR_ID.toString())))
                .andExpect(jsonPath("$.documentNumber", is("DUP-2026-0042")))
                .andExpect(jsonPath("$.type", is("DUPLICATA_MERCANTIL")))
                .andExpect(jsonPath("$.faceValueBrl", is("100000.00")))
                .andExpect(jsonPath("$.dueDate", is("2027-02-02")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.createdAt", is("2026-11-04T15:20:00Z")));
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Rejeita propriedade JSON desconhecida com 400 VALIDATION_ERROR")
    void shouldRejectUnknownJsonProperty() throws Exception {
        String json = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "DUP-2026-0042",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02",
                    "status": "SETTLED"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(receivableService);
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Rejeita faceValueBrl numérico sem aspas com 400 VALIDATION_ERROR")
    void shouldRejectUnquotedNumericFaceValueBrl() throws Exception {
        String json = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "DUP-2026-0042",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": 100000.00,
                    "dueDate": "2027-02-02"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(receivableService);
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Rejeita documentNumber numérico sem aspas com 400 VALIDATION_ERROR")
    void shouldRejectUnquotedNumericDocumentNumber() throws Exception {
        String json = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": 12345,
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(receivableService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-100.00", "1000000000.00", "100.123", "abc", ""})
    @DisplayName("POST /api/v1/receivables: Rejeita faceValueBrl inválido com 400 VALIDATION_ERROR e fieldErrors")
    void shouldRejectInvalidFaceValueBrl(String invalidValue) throws Exception {
        String json = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "DUP-2026-0042",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "%s",
                    "dueDate": "2027-02-02"
                }
                """.formatted(invalidValue);

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("faceValueBrl")));

        verifyNoInteractions(receivableService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "DUP@2026#0042",
            "DUP 2026 0042",
            "12345678901234567890123456789012345678901234567890123456789012345"
    })
    @DisplayName("POST /api/v1/receivables: Rejeita documentNumber inválido com 400 VALIDATION_ERROR e fieldErrors")
    void shouldRejectInvalidDocumentNumber(String invalidDoc) throws Exception {
        String json = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "%s",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2027-02-02"
                }
                """.formatted(invalidDoc);

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("documentNumber")));

        verifyNoInteractions(receivableService);
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Traduz AssignorNotFoundException em 404 ASSIGNOR_NOT_FOUND")
    void shouldReturn404WhenAssignorNotFoundOnCreate() throws Exception {
        when(receivableService.createReceivable(
                eq(ASSIGNOR_ID),
                eq("DUP-2026-0042"),
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(LocalDate.of(2027, 2, 2))
        )).thenThrow(new AssignorNotFoundException(ASSIGNOR_ID));

        String json = """
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
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("ASSIGNOR_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Traduz DuplicateReceivableDocumentException em 409 DUPLICATE_RECEIVABLE_DOCUMENT")
    void shouldReturn409WhenDuplicateReceivableDocument() throws Exception {
        when(receivableService.createReceivable(
                eq(ASSIGNOR_ID),
                eq("DUP-2026-0042"),
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(LocalDate.of(2027, 2, 2))
        )).thenThrow(new DuplicateReceivableDocumentException(ASSIGNOR_ID, "DUP-2026-0042"));

        String json = """
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
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_RECEIVABLE_DOCUMENT")));
    }

    @Test
    @DisplayName("POST /api/v1/receivables: Traduz InvalidDueDateException em 422 INVALID_DUE_DATE")
    void shouldReturn422WhenInvalidDueDate() throws Exception {
        LocalDate sameDay = LocalDate.of(2026, 11, 4);
        when(receivableService.createReceivable(
                eq(ASSIGNOR_ID),
                eq("DUP-2026-0042"),
                eq(ReceivableType.DUPLICATA_MERCANTIL),
                eq(new BigDecimal("100000.00")),
                eq(sameDay)
        )).thenThrow(new InvalidDueDateException(sameDay, sameDay));

        String json = """
                {
                    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
                    "documentNumber": "DUP-2026-0042",
                    "type": "DUPLICATA_MERCANTIL",
                    "faceValueBrl": "100000.00",
                    "dueDate": "2026-11-04"
                }
                """;

        mockMvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("INVALID_DUE_DATE")));
    }

    @Test
    @DisplayName("GET /api/v1/receivables: Aplica status=OPEN por padrão quando omitido e retorna 200 OK")
    void shouldListReceivablesWithDefaultStatusOpen() throws Exception {
        Receivable r1 = createSampleReceivable(RECEIVABLE_ID, ASSIGNOR_ID, "DUP-2026-0042", ReceivableStatus.OPEN);
        when(receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.OPEN)).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(RECEIVABLE_ID.toString())))
                .andExpect(jsonPath("$[0].status", is("OPEN")));

        verify(receivableService).listByAssignor(ASSIGNOR_ID, ReceivableStatus.OPEN);
    }

    @Test
    @DisplayName("GET /api/v1/receivables: Suporta filtro status=SETTLED e retorna 200 OK")
    void shouldListReceivablesWithStatusSettled() throws Exception {
        Receivable settled = createSampleReceivable(RECEIVABLE_ID, ASSIGNOR_ID, "DUP-2026-0099", ReceivableStatus.SETTLED);
        when(receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.SETTLED)).thenReturn(List.of(settled));

        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ID.toString())
                        .param("status", "SETTLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].documentNumber", is("DUP-2026-0099")))
                .andExpect(jsonPath("$[0].status", is("SETTLED")));
    }

    @Test
    @DisplayName("GET /api/v1/receivables: Retorna 200 OK com lista vazia [] quando cedente existe mas não possui títulos no status")
    void shouldReturnEmptyArrayWhenAssignorHasNoReceivables() throws Exception {
        when(receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.OPEN)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/receivables: Retorna 400 VALIDATION_ERROR quando assignorId está ausente")
    void shouldReturn400WhenAssignorIdParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/receivables"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("assignorId")));

        verifyNoInteractions(receivableService);
    }

    @Test
    @DisplayName("GET /api/v1/receivables: Retorna 400 VALIDATION_ERROR quando status é inválido")
    void shouldReturn400WhenStatusParamIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ID.toString())
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verifyNoInteractions(receivableService);
    }

    @Test
    @DisplayName("GET /api/v1/receivables: Retorna 404 ASSIGNOR_NOT_FOUND quando cedente não existe")
    void shouldReturn404WhenAssignorNotFoundOnList() throws Exception {
        when(receivableService.listByAssignor(ASSIGNOR_ID, ReceivableStatus.OPEN))
                .thenThrow(new AssignorNotFoundException(ASSIGNOR_ID));

        mockMvc.perform(get("/api/v1/receivables")
                        .param("assignorId", ASSIGNOR_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("ASSIGNOR_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/receivables/{id}: Retorna 200 OK quando encontrado e 404 RECEIVABLE_NOT_FOUND quando inexistente")
    void shouldGetReceivableByIdOrReturn404() throws Exception {
        Receivable receivable = createSampleReceivable(RECEIVABLE_ID, ASSIGNOR_ID, "DUP-2026-0042", ReceivableStatus.OPEN);
        when(receivableService.getById(RECEIVABLE_ID)).thenReturn(receivable);

        mockMvc.perform(get("/api/v1/receivables/{id}", RECEIVABLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(RECEIVABLE_ID.toString())))
                .andExpect(jsonPath("$.documentNumber", is("DUP-2026-0042")));

        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(receivableService.getById(missingId)).thenThrow(new ReceivableNotFoundException(missingId));

        mockMvc.perform(get("/api/v1/receivables/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", is("RECEIVABLE_NOT_FOUND")));
    }

    private Receivable createSampleReceivable(
            UUID id,
            UUID assignorId,
            String documentNumber,
            ReceivableStatus status
    ) {
        Assignor assignor = new Assignor("12345678000190", "Industria Alfa Ltda", FIXED_INSTANT);
        ReflectionTestUtils.setField(assignor, "id", assignorId);

        Receivable receivable = new Receivable(
                assignor,
                documentNumber,
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                LocalDate.of(2027, 2, 2),
                FIXED_INSTANT
        );
        ReflectionTestUtils.setField(receivable, "id", id);
        ReflectionTestUtils.setField(receivable, "status", status);
        return receivable;
    }
}
