package com.abhiman.taskflow.common;

/** The request is valid but clashes with the current state (duplicate name, illegal transition...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
