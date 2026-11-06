package com.claudiocarige.backendcreditengine.receivable.persistence;

import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entidade JPA representativa de títulos recebíveis (duplicatas ou cheques pré-datados).
 */
@Entity
@Table(name = "receivables")
public class Receivable {

    private static final BigDecimal MIN_FACE_VALUE = new BigDecimal("0.01");
    private static final BigDecimal MAX_FACE_VALUE = new BigDecimal("999999999.99");
    private static final Pattern DOCUMENT_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9._/-]{1,64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignor_id", nullable = false, updatable = false)
    private Assignor assignor;

    @Column(name = "document_number", nullable = false, length = 64, updatable = false)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private ReceivableType type;

    @Column(name = "face_value_brl", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal faceValueBrl;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReceivableStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Receivable() {
        // Construtor sem argumentos exigido pelo JPA/Hibernate
    }

    public Receivable(Assignor assignor, String documentNumber, ReceivableType type, BigDecimal faceValueBrl, LocalDate dueDate, Instant createdAt) {
        this.assignor = Objects.requireNonNull(assignor, "assignor cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null").truncatedTo(ChronoUnit.MICROS);

        Objects.requireNonNull(documentNumber, "documentNumber cannot be null");
        String normalizedDoc = documentNumber.trim().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_NUMBER_PATTERN.matcher(normalizedDoc).matches()) {
            throw new IllegalArgumentException("documentNumber does not match required format: " + normalizedDoc);
        }
        this.documentNumber = normalizedDoc;

        Objects.requireNonNull(faceValueBrl, "faceValueBrl cannot be null");
        if (faceValueBrl.scale() > 2) {
            throw new IllegalArgumentException("faceValueBrl scale cannot exceed 2 decimal places: " + faceValueBrl);
        }
        if (faceValueBrl.compareTo(MIN_FACE_VALUE) < 0 || faceValueBrl.compareTo(MAX_FACE_VALUE) > 0) {
            throw new IllegalArgumentException("faceValueBrl must be between " + MIN_FACE_VALUE + " and " + MAX_FACE_VALUE);
        }
        this.faceValueBrl = faceValueBrl;

        this.status = ReceivableStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public Assignor getAssignor() {
        return assignor;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public ReceivableType getType() {
        return type;
    }

    public BigDecimal getFaceValueBrl() {
        return faceValueBrl;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public ReceivableStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

