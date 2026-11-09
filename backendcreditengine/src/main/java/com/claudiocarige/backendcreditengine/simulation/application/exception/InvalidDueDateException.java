package com.claudiocarige.backendcreditengine.simulation.application.exception;

import java.time.LocalDate;

public class InvalidDueDateException extends RuntimeException {

    public InvalidDueDateException(LocalDate operationDate, LocalDate dueDate) {
        super("Due date (" + dueDate + ") must be strictly after operation date (" + operationDate + ")");
    }
}
