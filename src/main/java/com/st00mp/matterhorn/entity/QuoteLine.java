package com.st00mp.matterhorn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of a priced quote, as produced by the {@code PricingEngine} from the
 * accountant's grid. Amounts are in euro cents; a line always carries the
 * provenance of the input it was computed from, so the accountant can see at a
 * glance what is grid-driven and what rests on an assumption.
 */
@Getter
@Setter
@Entity
public class QuoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quoteRequestId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private long amountCents;

    @Column(columnDefinition = "TEXT")
    private String citation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provenance provenance;

    /** How much the line can be trusted, derived from the provenance of its inputs. */
    public enum Provenance {
        /** The amount comes straight from the grid, with no prospect input involved. */
        GRID,
        /** Computed from a variable quoted verbatim in the mail; {@code citation} carries the quote. */
        DETECTED,
        /** Computed from a variable the LLM produced without a verifiable quote; {@code citation} is null. */
        ASSUMED
    }
}
