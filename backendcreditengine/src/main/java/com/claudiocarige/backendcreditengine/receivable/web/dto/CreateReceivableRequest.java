package com.claudiocarige.backendcreditengine.receivable.web.dto;

import com.claudiocarige.backendcreditengine.common.web.StrictStringDeserializer;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateReceivableRequest(
        @NotNull(message = "assignorId is required")
        UUID assignorId,

        @NotBlank(message = "documentNumber is required")
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @Pattern(
                regexp = "^\\s*[A-Za-z0-9._/-]{1,64}\\s*$",
                message = "documentNumber must contain 1 to 64 allowed characters (A-Z, a-z, 0-9, ., _, /, -)"
        )
        String documentNumber,

        @NotNull(message = "type is required")
        ReceivableType type,

        @NotBlank(message = "faceValueBrl is required")
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @Pattern(
                regexp = "^(?:0\\.(?:0[1-9]|[1-9][0-9]?)|[1-9][0-9]{0,8}(?:\\.[0-9]{1,2})?)$",
                message = "faceValueBrl must be a decimal string between 0.01 and 999999999.99 with at most 2 decimal places"
        )
        String faceValueBrl,

        @NotNull(message = "dueDate is required")
        LocalDate dueDate
) {}
