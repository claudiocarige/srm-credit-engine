package com.claudiocarige.backendcreditengine.receivable.persistence;

import com.claudiocarige.backendcreditengine.TestcontainersConfiguration;
import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReceivablePersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID assignorId1;
    private UUID assignorId2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM receivables");
        jdbcTemplate.execute("DELETE FROM assignors");

        assignorId1 = UUID.randomUUID();
        assignorId2 = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                assignorId1, "11122233344", "Cedente 1");
        jdbcTemplate.update("INSERT INTO assignors (id, document, name, created_at) VALUES (?, ?, ?, now())",
                assignorId2, "55566677788", "Cedente 2");
    }

    @Test
    @DisplayName("Deve persistir e ler Receivable com round-trip JPA, confirmando LAZY loading e version inicial 0")
    void shouldPersistAndReadReceivableViaJpa() {
        LocalDate dueDate = LocalDate.of(2026-12-15, 12, 15);
        Instant createdAt = Instant.parse("2026-11-06T11:00:00Z");

        transactionTemplate.executeWithoutResult(status -> {
            Assignor assignor = entityManager.getReference(Assignor.class, assignorId1);
            Receivable receivable = new Receivable(
                    assignor,
                    "DUP-001",
                    ReceivableType.DUPLICATA_MERCANTIL,
                    new BigDecimal("2500.75"),
                    dueDate,
                    createdAt
            );

            entityManager.persist(receivable);
            entityManager.flush();
            UUID receivableId = receivable.getId();
            assertThat(receivableId).isNotNull();

            entityManager.clear();

            Receivable found = entityManager.find(Receivable.class, receivableId);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(receivableId);
            assertThat(found.getDocumentNumber()).isEqualTo("DUP-001");
            assertThat(found.getType()).isEqualTo(ReceivableType.DUPLICATA_MERCANTIL);
            assertThat(found.getFaceValueBrl()).isEqualByComparingTo("2500.75");
            assertThat(found.getDueDate()).isEqualTo(dueDate);
            assertThat(found.getStatus()).isEqualTo(ReceivableStatus.OPEN);
            assertThat(found.getVersion()).isEqualTo(0L);
            assertThat(found.getCreatedAt()).isEqualTo(createdAt.truncatedTo(ChronoUnit.MICROS));

            // Validação de LAZY loading sem inicialização prematura
            assertThat(Hibernate.isInitialized(found.getAssignor())).isFalse();
            assertThat(found.getAssignor().getName()).isEqualTo("Cedente 1");
            assertThat(Hibernate.isInitialized(found.getAssignor())).isTrue();
        });
    }

    @Test
    @DisplayName("Deve rejeitar duplicidade de document_number para o mesmo cedente (uk_receivables_assignor_document_number 23505)")
    void shouldRejectDuplicateDocumentNumberForSameAssignor() {
        jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-100', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'OPEN', 0, now())
                """, UUID.randomUUID(), assignorId1);

        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-100', 'CHEQUE_PRE_DATADO', 200.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23505",
                "uk_receivables_assignor_document_number"
        );
    }

    @Test
    @DisplayName("Deve permitir mesmo document_number para cedentes distintos")
    void shouldAllowSameDocumentNumberForDifferentAssignors() {
        jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-200', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'OPEN', 0, now())
                """, UUID.randomUUID(), assignorId1);

        int rows = jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-200', 'CHEQUE_PRE_DATADO', 200.00, '2026-12-01', 'OPEN', 0, now())
                """, UUID.randomUUID(), assignorId2);

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Deve rejeitar assignor_id inexistente violando fk_receivables_assignor (23503)")
    void shouldRejectNonExistentAssignorId() {
        UUID nonExistentAssignorId = UUID.randomUUID();

        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-300', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), nonExistentAssignorId),
                "23503",
                "fk_receivables_assignor"
        );
    }

    @Test
    @DisplayName("Deve rejeitar exclusão de cedente que possui recebíveis vinculados (RESTRICT 23503)")
    void shouldRejectDeleteOfAssignorWithReceivables() {
        jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-400', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'OPEN', 0, now())
                """, UUID.randomUUID(), assignorId1);

        assertConstraintViolation(
                () -> jdbcTemplate.update("DELETE FROM assignors WHERE id = ?", assignorId1),
                "23503",
                "fk_receivables_assignor"
        );
    }

    @Test
    @DisplayName("Deve validar limites contratuais de face_value_brl no banco: [0.01, 999999999.99] (ck_receivables_face_value_brl_range 23514)")
    void shouldValidateFaceValueBrlRange() {
        // Rejeita 0.00
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-500', 'DUPLICATA_MERCANTIL', 0.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23514",
                "ck_receivables_face_value_brl_range"
        );

        // Rejeita 1000000000.00
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-501', 'DUPLICATA_MERCANTIL', 1000000000.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23514",
                "ck_receivables_face_value_brl_range"
        );

        // Aceita bordas 0.01 e 999999999.99
        int row1 = jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-MIN', 'DUPLICATA_MERCANTIL', 0.01, '2026-12-01', 'OPEN', 0, now())
                """, UUID.randomUUID(), assignorId1);
        assertThat(row1).isEqualTo(1);

        int row2 = jdbcTemplate.update("""
                INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                VALUES (?, ?, 'DUP-MAX', 'DUPLICATA_MERCANTIL', 999999999.99, '2026-12-01', 'OPEN', 0, now())
                """, UUID.randomUUID(), assignorId1);
        assertThat(row2).isEqualTo(1);
    }

    @Test
    @DisplayName("Deve rejeitar overflow numérico de face_value_brl excedendo NUMERIC(19,2) (22003)")
    void shouldRejectNumericOverflow() {
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-OF', 'DUPLICATA_MERCANTIL', 100000000000000000.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "22003",
                null
        );
    }

    @Test
    @DisplayName("Deve rejeitar document_number não normalizado no banco (ck_receivables_document_number_format 23514)")
    void shouldRejectNonNormalizedDocumentNumberInDatabase() {
        // Minúsculo
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'dup-lower', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23514",
                "ck_receivables_document_number_format"
        );

        // Com espaços
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP 001', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23514",
                "ck_receivables_document_number_format"
        );
    }

    @Test
    @DisplayName("Deve rejeitar type e status inválidos no banco (23514)")
    void shouldRejectInvalidTypeAndStatusInDatabase() {
        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-TYP', 'INVALID_TYPE', 100.00, '2026-12-01', 'OPEN', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23514",
                "ck_receivables_type"
        );

        assertConstraintViolation(
                () -> jdbcTemplate.update("""
                        INSERT INTO receivables (id, assignor_id, document_number, type, face_value_brl, due_date, status, version, created_at)
                        VALUES (?, ?, 'DUP-STA', 'DUPLICATA_MERCANTIL', 100.00, '2026-12-01', 'INVALID_STATUS', 0, now())
                        """, UUID.randomUUID(), assignorId1),
                "23514",
                "ck_receivables_status"
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
