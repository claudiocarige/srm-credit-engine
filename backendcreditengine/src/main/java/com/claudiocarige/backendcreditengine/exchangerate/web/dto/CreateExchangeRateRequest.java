package com.claudiocarige.backendcreditengine.exchangerate.web.dto;

import com.claudiocarige.backendcreditengine.common.web.StrictStringDeserializer;
import com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateExchangeRateRequest(
    @NotNull(message = "currency is required")
    CurrencyCode currency,

    @NotBlank(message = "rateBrl is required")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Pattern(
        regexp = "^(?:0\\.(?:[1-9][0-9]{0,5}|0[1-9][0-9]{0,4}|00[1-9][0-9]{0,3}|000[1-9][0-9]{0,2}|0000[1-9][0-9]?|00000[1-9])|[1-9][0-9]{0,6}(?:\\.[0-9]{1,6})?)$",
        message = "rateBrl must be a decimal string between 0.000001 and 9999999.999999 with at most 6 decimal places"
    )
    String rateBrl,

    @NotNull(message = "effectiveAt is required")
    Instant effectiveAt
) {
    @AssertTrue(message = "currency must be USD")
    public boolean isCurrency() {
        return currency == null || currency == CurrencyCode.USD;
    }

    @AssertTrue(message = "effectiveAt must have microsecond precision")
    public boolean isEffectiveAt() {
        return effectiveAt == null || effectiveAt.getNano() % 1_000 == 0;
    }
}
