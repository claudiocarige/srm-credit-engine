package com.claudiocarige.backendcreditengine.assignor.web.dto;

import com.claudiocarige.backendcreditengine.assignor.persistence.Assignor;
import java.util.UUID;

public record AssignorResponse(
        UUID id,
        String document,
        String name
) {
    public static AssignorResponse from(Assignor assignor) {
        return new AssignorResponse(
                assignor.getId(),
                assignor.getDocument(),
                assignor.getName()
        );
    }
}
