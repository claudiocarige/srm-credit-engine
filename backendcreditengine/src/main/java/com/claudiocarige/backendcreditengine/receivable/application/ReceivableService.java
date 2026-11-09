package com.claudiocarige.backendcreditengine.receivable.application;

import com.claudiocarige.backendcreditengine.assignor.application.exception.AssignorNotFoundException;
import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import com.claudiocarige.backendcreditengine.assignor.persistence.AssignorRepository;
import com.claudiocarige.backendcreditengine.common.exception.InvalidDueDateException;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.application.exception.DuplicateReceivableDocumentException;
import com.claudiocarige.backendcreditengine.receivable.application.exception.ReceivableNotFoundException;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import com.claudiocarige.backendcreditengine.receivable.persistence.Receivable;
import com.claudiocarige.backendcreditengine.receivable.persistence.ReceivableRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivableService {

    private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";
    private static final String UK_RECEIVABLES_ASSIGNOR_DOCUMENT_NUMBER = "uk_receivables_assignor_document_number";

    private final ReceivableRepository receivableRepository;
    private final AssignorRepository assignorRepository;
    private final Clock clock;

    public ReceivableService(
            ReceivableRepository receivableRepository,
            AssignorRepository assignorRepository,
            Clock clock
    ) {
        this.receivableRepository = Objects.requireNonNull(receivableRepository, "receivableRepository cannot be null");
        this.assignorRepository = Objects.requireNonNull(assignorRepository, "assignorRepository cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Transactional
    public Receivable createReceivable(
            UUID assignorId,
            String rawDocumentNumber,
            ReceivableType type,
            BigDecimal faceValueBrl,
            LocalDate dueDate
    ) {
        Objects.requireNonNull(assignorId, "assignorId cannot be null");
        Objects.requireNonNull(rawDocumentNumber, "rawDocumentNumber cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(faceValueBrl, "faceValueBrl cannot be null");
        Objects.requireNonNull(dueDate, "dueDate cannot be null");

        Instant createdAt = clock.instant();
        LocalDate operationDate = LocalDate.ofInstant(createdAt, ZoneOffset.UTC);

        if (!dueDate.isAfter(operationDate)) {
            throw new InvalidDueDateException(operationDate, dueDate);
        }

        Assignor assignor = assignorRepository.findById(assignorId)
                .orElseThrow(() -> new AssignorNotFoundException(assignorId));

        String normalizedDoc = rawDocumentNumber.trim().toUpperCase(Locale.ROOT);

        if (receivableRepository.existsByAssignor_IdAndDocumentNumber(assignorId, normalizedDoc)) {
            throw new DuplicateReceivableDocumentException(assignorId, normalizedDoc);
        }

        Receivable receivable = new Receivable(
                assignor,
                normalizedDoc,
                type,
                faceValueBrl,
                dueDate,
                createdAt
        );

        try {
            return receivableRepository.saveAndFlush(receivable);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateReceivableConstraint(ex)) {
                throw new DuplicateReceivableDocumentException(assignorId, normalizedDoc, ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Receivable> listByAssignor(UUID assignorId, ReceivableStatus status) {
        Objects.requireNonNull(assignorId, "assignorId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        if (!assignorRepository.existsById(assignorId)) {
            throw new AssignorNotFoundException(assignorId);
        }

        return receivableRepository.findByAssignor_IdAndStatusOrderByDueDateAscDocumentNumberAsc(assignorId, status);
    }

    @Transactional(readOnly = true)
    public Receivable getById(UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        return receivableRepository.findById(id)
                .orElseThrow(() -> new ReceivableNotFoundException(id));
    }

    private boolean isDuplicateReceivableConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof PSQLException psql) {
                boolean matchesSqlState = SQL_STATE_UNIQUE_VIOLATION.equals(psql.getSQLState());
                String constraint = psql.getServerErrorMessage() != null
                        ? psql.getServerErrorMessage().getConstraint()
                        : null;
                boolean matchesConstraint = UK_RECEIVABLES_ASSIGNOR_DOCUMENT_NUMBER.equalsIgnoreCase(constraint);
                if (matchesSqlState && matchesConstraint) {
                    return true;
                }
            } else if (cause instanceof ConstraintViolationException cve) {
                boolean matchesSqlState = SQL_STATE_UNIQUE_VIOLATION.equals(cve.getSQLState());
                boolean matchesConstraint = UK_RECEIVABLES_ASSIGNOR_DOCUMENT_NUMBER.equalsIgnoreCase(cve.getConstraintName());
                if (matchesSqlState && matchesConstraint) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
