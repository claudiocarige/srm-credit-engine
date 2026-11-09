package com.claudiocarige.backendcreditengine.receivable.application.exception;

import java.util.UUID;

public class DuplicateReceivableDocumentException extends RuntimeException {

    public DuplicateReceivableDocumentException(UUID assignorId, String documentNumber) {
        super("Receivable with documentNumber '" + documentNumber + "' already exists for assignor " + assignorId);
    }

    public DuplicateReceivableDocumentException(UUID assignorId, String documentNumber, Throwable cause) {
        super("Receivable with documentNumber '" + documentNumber + "' already exists for assignor " + assignorId, cause);
    }
}
