package com.claudiocarige.backendcreditengine.simulation.web.dto;

import com.claudiocarige.backendcreditengine.common.web.StrictStringDeserializer;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import com.claudiocarige.backendcreditengine.pricing.domain.model.ReceivableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SimulationRequest(
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
        LocalDate dueDate,

        @NotNull(message = "paymentCurrency is required")
        CurrencyCode paymentCurrency
) {
}
