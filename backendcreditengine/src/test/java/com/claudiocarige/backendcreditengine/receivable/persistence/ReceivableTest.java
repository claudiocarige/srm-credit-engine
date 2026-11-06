package com.claudiocarige.backendcreditengine.receivable.persistence;

import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceivableTest {

    private final Assignor defaultAssignor = new Assignor(
            "12345678901",
            "Cedente Exemplo",
            Instant.parse("2026-11-06T12:00:00Z")
    );

    @Test
    @DisplayName("Deve instanciar Receivable com sucesso normalizando documentNumber e inicializando status OPEN")
    void shouldCreateReceivableSuccessfully() {
        Instant now = Instant.parse("2026-11-06T12:30:45.123456789Z");
        LocalDate dueDate = LocalDate.of(2026-12-06, 12, 1);

        Receivable receivable = new Receivable(
                defaultAssignor,
                "  dup-123.a/01_b  ",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("1500.50"),
                dueDate,
                now
        );

        assertThat(receivable.getAssignor()).isEqualTo(defaultAssignor);
        assertThat(receivable.getDocumentNumber()).isEqualTo("DUP-123.A/01_B");
        assertThat(receivable.getType()).isEqualTo(ReceivableType.DUPLICATA_MERCANTIL);
        assertThat(receivable.getFaceValueBrl()).isEqualByComparingTo("1500.50");
        assertThat(receivable.getDueDate()).isEqualTo(dueDate);
        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.OPEN);
        assertThat(receivable.getCreatedAt()).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("Deve aceitar valores de face nos limites contratuais [0.01, 999999999.99]")
    void shouldAcceptBoundaryFaceValues() {
        LocalDate dueDate = LocalDate.of(2026-12-01, 1, 1);
        Instant now = Instant.now();

        Receivable minReceivable = new Receivable(
                defaultAssignor,
                "DOC-MIN",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("0.01"),
                dueDate,
                now
        );
        assertThat(minReceivable.getFaceValueBrl()).isEqualByComparingTo("0.01");

        Receivable maxReceivable = new Receivable(
                defaultAssignor,
                "DOC-MAX",
                ReceivableType.CHEQUE_PRE_DATADO,
                new BigDecimal("999999999.99"),
                dueDate,
                now
        );
        assertThat(maxReceivable.getFaceValueBrl()).isEqualByComparingTo("999999999.99");
    }

    @Test
    @DisplayName("Deve rejeitar escala monetária superior a 2 casas decimais, inclusive com zeros adicionais")
    void shouldRejectFaceValueWithScaleGreaterThanTwo() {
        LocalDate dueDate = LocalDate.of(2026-12-01, 1, 1);
        Instant now = Instant.now();

        assertThatThrownBy(() -> new Receivable(
                defaultAssignor,
                "DOC-1",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100.000"),
                dueDate,
                now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("scale cannot exceed 2 decimal places");

        assertThatThrownBy(() -> new Receivable(
                defaultAssignor,
                "DOC-2",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100.555"),
                dueDate,
                now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("scale cannot exceed 2 decimal places");
    }

    @Test
    @DisplayName("Deve rejeitar valor de face fora do intervalo permitido [0.01, 999999999.99]")
    void shouldRejectFaceValueOutOfRange() {
        LocalDate dueDate = LocalDate.of(2026-12-01, 1, 1);
        Instant now = Instant.now();

        assertThatThrownBy(() -> new Receivable(
                defaultAssignor,
                "DOC-ZERO",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("0.00"),
                dueDate,
                now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("faceValueBrl must be between 0.01 and 999999999.99");

        assertThatThrownBy(() -> new Receivable(
                defaultAssignor,
                "DOC-NEG",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("-10.00"),
                dueDate,
                now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("faceValueBrl must be between 0.01 and 999999999.99");

        assertThatThrownBy(() -> new Receivable(
                defaultAssignor,
                "DOC-EXCESS",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("1000000000.00"),
                dueDate,
                now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("faceValueBrl must be between 0.01 and 999999999.99");
    }

    @Test
    @DisplayName("Deve rejeitar campos obrigatórios nulos")
    void shouldRejectNullFields() {
        LocalDate dueDate = LocalDate.of(2026-12-01, 1, 1);
        Instant now = Instant.now();
        BigDecimal validValue = new BigDecimal("100.00");

        assertThatThrownBy(() -> new Receivable(null, "DOC-1", ReceivableType.DUPLICATA_MERCANTIL, validValue, dueDate, now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("assignor cannot be null");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, null, ReceivableType.DUPLICATA_MERCANTIL, validValue, dueDate, now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("documentNumber cannot be null");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, "DOC-1", null, validValue, dueDate, now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type cannot be null");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, "DOC-1", ReceivableType.DUPLICATA_MERCANTIL, null, dueDate, now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("faceValueBrl cannot be null");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, "DOC-1", ReceivableType.DUPLICATA_MERCANTIL, validValue, null, now))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("dueDate cannot be null");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, "DOC-1", ReceivableType.DUPLICATA_MERCANTIL, validValue, dueDate, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt cannot be null");
    }

    @Test
    @DisplayName("Deve rejeitar documentNumber com caracteres inválidos, vazio ou superior a 64 caracteres")
    void shouldRejectInvalidDocumentNumbers() {
        LocalDate dueDate = LocalDate.of(2026-12-01, 1, 1);
        Instant now = Instant.now();
        BigDecimal validValue = new BigDecimal("100.00");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, "   ", ReceivableType.DUPLICATA_MERCANTIL, validValue, dueDate, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match required format");

        assertThatThrownBy(() -> new Receivable(defaultAssignor, "DOC#123", ReceivableType.DUPLICATA_MERCANTIL, validValue, dueDate, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match required format");

        String over64 = "A".repeat(65);
        assertThatThrownBy(() -> new Receivable(defaultAssignor, over64, ReceivableType.DUPLICATA_MERCANTIL, validValue, dueDate, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match required format");
    }
}

