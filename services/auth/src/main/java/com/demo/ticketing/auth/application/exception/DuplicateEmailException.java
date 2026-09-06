package com.demo.ticketing.auth.application.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("An account with this email already exists");
    }
}
