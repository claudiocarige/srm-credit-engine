package com.claudiocarige.backendcreditengine.receivable.application.exception;

import java.util.UUID;

public class ReceivableNotFoundException extends RuntimeException {

    public ReceivableNotFoundException(UUID receivableId) {
        super("Receivable not found with id: " + receivableId);
    }
}
