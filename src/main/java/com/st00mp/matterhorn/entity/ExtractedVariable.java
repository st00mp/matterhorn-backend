package com.st00mp.matterhorn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class ExtractedVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quoteRequestId;

    @Column(name = "variable_key", nullable = false)
    private String key;

    @Column(name = "variable_value")
    private String value;

    @Column(columnDefinition = "TEXT")
    private String citation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provenance provenance;

    /**
     * How the value was obtained — decided by deterministic verification,
     * never by the LLM.
     */
    public enum Provenance {
        /** The citation exists verbatim in the mail body. */
        SOURCED,
        /** A value exists but no citation could be verified in the mail body. */
        ASSUMED,
        /** No value found in the mail; {@code value} is null. */
        MISSING
    }
}
