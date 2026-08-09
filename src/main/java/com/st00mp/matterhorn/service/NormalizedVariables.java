package com.st00mp.matterhorn.service;

import com.st00mp.matterhorn.entity.ExtractedVariable;

/**
 * The prospect variables the pricing engine needs, in typed form: the free-text
 * {@link ExtractedVariable}s produced by the LLM, once turned into values the engine
 * can compute with. Produced by {@link ExtractedVariableNormalizer}.
 *
 * <p>Any variable the normalizer could not interpret with confidence comes back
 * {@link NormalizedVariable#missing() missing} — and a single missing variable is
 * enough for the engine to price nothing at all.
 */
public record NormalizedVariables(
        NormalizedVariable<LegalForm> legalForm,
        NormalizedVariable<Integer> volume,
        NormalizedVariable<VatRegime> vatRegime) {

    /** The only two legal forms the grid prices; anything else (ASBL…) is missing. */
    public enum LegalForm {
        COMPANY,
        INDIVIDUAL
    }

    /** The only two VAT filing frequencies the grid prices; anything else is missing. */
    public enum VatRegime {
        MONTHLY,
        QUARTERLY
    }

    /**
     * A typed value with the provenance and citation of the {@link ExtractedVariable}
     * it came from, so the quote lines can keep pointing back at the mail.
     *
     * @param value      the interpreted value, null when the variable is unusable
     * @param provenance the provenance of the source variable, null when missing
     * @param citation   the mail passage backing the value, null unless {@code SOURCED}
     */
    public record NormalizedVariable<T>(T value, ExtractedVariable.Provenance provenance, String citation) {

        /** A variable that cannot be used for pricing — absent, unparseable, or off-grid. */
        public static <T> NormalizedVariable<T> missing() {
            return new NormalizedVariable<>(null, null, null);
        }

        public boolean isMissing() {
            return value == null;
        }
    }
}
