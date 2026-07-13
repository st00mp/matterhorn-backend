package com.st00mp.matterhorn.exception;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(Long id) {
        super("AgentTemplate %d not found".formatted(id));
    }
}
