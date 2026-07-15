package com.st00mp.matterhorn.exception;

import java.util.Set;

public class UnresolvedPlaceholderException extends RuntimeException {
    public UnresolvedPlaceholderException(Set<String> keys) {
        super("Unresolved placeholders (no value provided): %s".formatted(keys));
    }
}
