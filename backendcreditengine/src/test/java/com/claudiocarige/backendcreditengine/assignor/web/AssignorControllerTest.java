package com.claudiocarige.backendcreditengine.assignor.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claudiocarige.backendcreditengine.assignor.application.AssignorService;
import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.common.web.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssignorController.class)
@Import(GlobalExceptionHandler.class)
class AssignorControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-04T15:20:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssignorService assignorService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
    }

    @Test
    @DisplayName("GET /api/v1/assignors: Retorna 200 OK com lista de cedentes ordenada por nome")
    void shouldListAssignorsOrderedByName() throws Exception {
        UUID idBeta = UUID.fromString("9a25f560-dffb-478b-8b49-2c5da09c0002");
        Assignor beta = new Assignor("98765432000110", "Comercio Beta S.A.", FIXED_INSTANT);
        ReflectionTestUtils.setField(beta, "id", idBeta);

        UUID idAlfa = UUID.fromString("8f14e45f-ceea-467a-9a38-1b4c9f8b0001");
        Assignor alfa = new Assignor("12345678000190", "Industria Alfa Ltda", FIXED_INSTANT);
        ReflectionTestUtils.setField(alfa, "id", idAlfa);

        when(assignorService.listAssignors()).thenReturn(List.of(beta, alfa));

        mockMvc.perform(get("/api/v1/assignors")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(idBeta.toString())))
                .andExpect(jsonPath("$[0].document", is("98765432000110")))
                .andExpect(jsonPath("$[0].name", is("Comercio Beta S.A.")))
                .andExpect(jsonPath("$[1].id", is(idAlfa.toString())))
                .andExpect(jsonPath("$[1].document", is("12345678000190")))
                .andExpect(jsonPath("$[1].name", is("Industria Alfa Ltda")));
    }
}
