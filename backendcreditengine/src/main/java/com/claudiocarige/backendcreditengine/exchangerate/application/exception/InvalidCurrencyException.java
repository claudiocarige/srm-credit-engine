package com.claudiocarige.backendcreditengine.exchangerate.application.exception;

public class InvalidCurrencyException extends RuntimeException {

    public InvalidCurrencyException(String message) {
        super(message);
    }
}

