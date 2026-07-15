package com.st00mp.matterhorn.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record FieldDefinition(
        @NotBlank String key,
        @NotBlank String label,
        @NotBlank String type,
        Kind kind,
        String help
) {
    public enum Kind {
        @JsonProperty("prompt") PROMPT,
        @JsonProperty("engine") ENGINE,
        @JsonProperty("both") BOTH
    }
}
