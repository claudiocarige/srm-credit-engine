package com.claudiocarige.backendcreditengine.assignor.application.exception;

import java.util.UUID;

public class AssignorNotFoundException extends RuntimeException {

    public AssignorNotFoundException(UUID assignorId) {
        super("Assignor not found with id: " + assignorId);
    }
}
