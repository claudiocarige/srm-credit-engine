package com.claudiocarige.backendcreditengine.receivable.web.dto;

import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import com.claudiocarige.backendcreditengine.receivable.domain.ReceivableStatus;
import com.claudiocarige.backendcreditengine.receivable.persistence.Receivable;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableResponse(
        UUID id,
        UUID assignorId,
        String documentNumber,
        ReceivableType type,
        String faceValueBrl,
        LocalDate dueDate,
        ReceivableStatus status,
        Instant createdAt
) {
    public static ReceivableResponse from(Receivable receivable) {
        return new ReceivableResponse(
                receivable.getId(),
                receivable.getAssignor().getId(),
                receivable.getDocumentNumber(),
                receivable.getType(),
                receivable.getFaceValueBrl().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                receivable.getDueDate(),
                receivable.getStatus(),
                receivable.getCreatedAt()
        );
    }
}
