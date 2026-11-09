package com.claudiocarige.backendcreditengine.common.exception;

import java.time.LocalDate;

public class InvalidDueDateException extends RuntimeException {

    public InvalidDueDateException(LocalDate operationDate, LocalDate dueDate) {
        super("Due date (" + dueDate + ") must be strictly after operation date (" + operationDate + ")");
    }
}
