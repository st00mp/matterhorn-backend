package com.st00mp.matterhorn.exception;

public class IncomingMailNotFoundException extends RuntimeException {
    public IncomingMailNotFoundException(Long id) {
        super("IncomingMail %d not found".formatted(id));
    }
}
