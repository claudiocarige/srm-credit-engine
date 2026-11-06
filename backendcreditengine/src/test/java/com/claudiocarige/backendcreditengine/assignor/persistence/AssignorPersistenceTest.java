package com.claudiocarige.backendcreditengine.assignor.persistence;

import com.claudiocarige.backendcreditengine.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AssignorPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM receivables");
        jdbcTemplate.execute("DELETE FROM assignors");
    }

    @Test
    @DisplayName("Deve persistir e ler cedente via JPA com sucesso")
    void shouldPersistAndReadAssignorViaJpa() {
        Instant createdAt = Instant.parse("2026-11-06T10:00:00Z");
        Assignor assignor = new Assignor("12345678901", "Cedente Alpha Ltda", createdAt);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(assignor);
            entityManager.flush();
            UUID id = assignor.getId();
            assertThat(id).isNotNull();

            entityManager.clear();

            Assignor found = entityManager.find(Assignor.class, id);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(id);
            assertThat(found.getDocument()).isEqualTo("12345678901");
            assertThat(found.getName()).isEqualTo("Cedente Alpha Ltda");
            assertThat(found.getCreatedAt()).isEqualTo(createdAt.truncatedTo(ChronoUnit.MICROS));
        });
    }

    @Test
    @DisplayName("Deve ler via JPA um cedente inserido diretamente por SQL")
    void shouldReadAssignorInsertedViaSql() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        jdbcTemplate.update(
                "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, ?)",
                id, "98765432000199", "Empresa SQL S/A", java.sql.Timestamp.from(createdAt)
        );

        transactionTemplate.executeWithoutResult(status -> {
            Assignor found = entityManager.find(Assignor.class, id);
            assertThat(found).isNotNull();
            assertThat(found.getDocument()).isEqualTo("98765432000199");
            assertThat(found.getName()).isEqualTo("Empresa SQL S/A");
        });
    }

    @Test
    @DisplayName("Deve rejeitar documento duplicado violando uk_assignors_document (23505)")
    void shouldRejectDuplicateDocument() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        String document = "11122233344";

        jdbcTemplate.update(
                "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                id1, document, "Cedente 1"
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                        id2, document, "Cedente 2"
                ),
                "23505",
                "uk_assignors_document"
        );
    }

    @Test
    @DisplayName("Deve rejeitar documento com tamanho inválido ou letras violando ck_assignors_document_digits (23514) ou tamanho VARCHAR(14) (22001)")
    void shouldRejectInvalidDocumentFormat() {
        // 10 dígitos (inválido: regex exige 11 a 14 dígitos)
        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                        UUID.randomUUID(), "1234567890", "Nome"
                ),
                "23514",
                "ck_assignors_document_digits"
        );

        // 15 dígitos (estoura VARCHAR(14))
        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                        UUID.randomUUID(), "123456789012345", "Nome"
                ),
                "22001",
                null
        );

        // Contendo letras
        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                        UUID.randomUUID(), "1234567890A", "Nome"
                ),
                "23514",
                "ck_assignors_document_digits"
        );
    }

    @Test
    @DisplayName("Deve rejeitar nome em branco violando ck_assignors_name_not_blank (23514)")
    void shouldRejectBlankName() {
        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                        UUID.randomUUID(), "12345678901", "   "
                ),
                "23514",
                "ck_assignors_name_not_blank"
        );
    }

    @Test
    @DisplayName("Deve rejeitar colunas obrigatórias nulas (23502)")
    void shouldRejectNullColumns() {
        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, null, ?, now())",
                        UUID.randomUUID(), "Nome"
                ),
                "23502",
                null
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update(
                        "INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, null, now())",
                        UUID.randomUUID(), "12345678901"
                ),
                "23502",
                null
        );
    }

    private void assertConstraintViolation(Runnable action, String expectedSqlState, String expectedConstraint) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    PSQLException psql = extractPsqlException(ex);
                    assertThat((Throwable) psql).as("PSQLException esperada na cadeia de causas").isNotNull();
                    assertThat(psql.getSQLState()).isEqualTo(expectedSqlState);
                    if (expectedConstraint != null) {
                        assertThat(psql.getServerErrorMessage()).isNotNull();
                        assertThat(psql.getServerErrorMessage().getConstraint()).isEqualTo(expectedConstraint);
                    }
                });
    }

    private PSQLException extractPsqlException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof PSQLException psql) {
                return psql;
            }
            current = current.getCause();
        }
        return null;
    }
}
