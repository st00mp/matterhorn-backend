package com.st00mp.matterhorn.exception;

import java.util.Set;

public class IncompleteInstanceException extends RuntimeException {
    public IncompleteInstanceException(Set<String> missingKeys) {
        super("Missing or empty values for required fields: %s".formatted(missingKeys));
    }
}
