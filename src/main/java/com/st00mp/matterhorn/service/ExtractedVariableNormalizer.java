package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.service.NormalizedVariables.NormalizedVariable.missing;

import com.st00mp.matterhorn.entity.ExtractedVariable;
import com.st00mp.matterhorn.service.NormalizedVariables.LegalForm;
import com.st00mp.matterhorn.service.NormalizedVariables.NormalizedVariable;
import com.st00mp.matterhorn.service.NormalizedVariables.VatRegime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Turns the free-text {@link ExtractedVariable}s produced by the extractor into the
 * typed {@link NormalizedVariables} the {@link PricingEngine} computes with.
 *
 * <p>The rule throughout is pessimistic: whatever cannot be interpreted with
 * certainty comes back {@link NormalizedVariable#missing() missing} rather than
 * guessed. The engine then prices nothing and a human takes over — an empty quote
 * costs a follow-up, a wrong quote costs the client's trust.
 *
 * <p>A variable is unusable, whatever else it carries, when it is absent from the
 * list, has a null value, or has provenance {@code MISSING}.
 */
@Service
public class ExtractedVariableNormalizer {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");

    /** Beyond that, a run of digits is not a document count — and would not fit in an int. */
    private static final int MAX_DIGITS = 9;

    /** Two numbers further apart than that are not the bounds of one range. */
    private static final int MAX_RANGE_RATIO = 10;

    /**
     * Normalizes the three prospect variables the grid prices. Unknown keys are
     * ignored; a key present twice is read from its first occurrence.
     */
    public NormalizedVariables normalize(List<ExtractedVariable> variables) {
        return new NormalizedVariables(
                normalizeLegalForm(findByKey(variables, "client_legal_form")),
                normalizeVolume(findByKey(variables, "client_document_volume")),
                normalizeVatRegime(findByKey(variables, "client_vat_regime")));
    }

    private ExtractedVariable findByKey(List<ExtractedVariable> variables, String key) {
        return variables.stream()
                .filter(v -> key.equals(v.getKey()))
                .findFirst()
                .orElse(null);
    }

    /** The prompt constrains the value to a token; anything else is treated as unusable. */
    private NormalizedVariable<LegalForm> normalizeLegalForm(ExtractedVariable v) {
        if (isUnusable(v)) {
            return missing();
        }

        LegalForm form =
                switch (v.getValue()) {
                    case "COMPANY" -> LegalForm.COMPANY;
                    case "INDIVIDUAL" -> LegalForm.INDIVIDUAL;
                    default -> null;
                };

        return form == null ? missing() : new NormalizedVariable<>(form, v.getProvenance(), v.getCitation());
    }

    /**
     * Reads a monthly document count out of free text. Unlike the two token
     * variables, the volume stays a sentence the prompt copies from the mail
     * ("environ 80 factures par mois"), so it is parsed here rather than upstream.
     *
     * <p>The text read is the citation when the variable is {@code SOURCED} — the
     * prospect's own words are more reliable than the model's rewording — and the
     * value otherwise. From it:
     * <ul>
     *   <li>no monthly marker ({@code par mois}, {@code mensuel}) → missing: the grid
     *       prices a monthly volume and converting a yearly one is a guess;
     *   <li>one number → that number;
     *   <li>two numbers → their lower median, a range being read as its middle;
     *   <li>anything else → missing, the text being too ambiguous to price on.
     * </ul>
     *
     * <p>Runs of digits too long to be a document count are ignored, so a citation
     * carrying one reads as having one number less.
     */
    private NormalizedVariable<Integer> normalizeVolume(ExtractedVariable v) {
        if (isUnusable(v)) {
            return missing();
        }

        String sourceText = v.getProvenance() == ExtractedVariable.Provenance.SOURCED ? v.getCitation() : v.getValue();
        if (sourceText == null) {
            return missing();
        }

        String lower = sourceText.toLowerCase();
        if (!lower.contains("par mois") && !lower.contains("mensuel")) {
            return missing();
        }

        List<Integer> numbers = extractIntegers(sourceText);
        Integer volume =
                switch (numbers.size()) {
                    case 1 -> numbers.get(0);
                    case 2 -> rangeMiddle(numbers.get(0), numbers.get(1));
                    default -> null;
                };

        return volume == null ? missing() : new NormalizedVariable<>(volume, v.getProvenance(), v.getCitation());
    }

    /** The prompt constrains the value to a token; anything else is treated as unusable. */
    private NormalizedVariable<VatRegime> normalizeVatRegime(ExtractedVariable v) {
        if (isUnusable(v)) {
            return missing();
        }

        VatRegime regime =
                switch (v.getValue()) {
                    case "MONTHLY" -> VatRegime.MONTHLY;
                    case "QUARTERLY" -> VatRegime.QUARTERLY;
                    default -> null;
                };

        return regime == null ? missing() : new NormalizedVariable<>(regime, v.getProvenance(), v.getCitation());
    }

    /** {@code MISSING} wins even on a non-null value: a contradictory state is not priced. */
    private boolean isUnusable(ExtractedVariable v) {
        return v == null || v.getValue() == null || v.getProvenance() == ExtractedVariable.Provenance.MISSING;
    }

    /**
     * Lower median of the two numbers, or null when they are too far apart to be the
     * bounds of a single range: in "depuis 2020, 30 factures par mois" the first number
     * is a year, and averaging it would invoice a volume nobody ever mentioned.
     */
    private Integer rangeMiddle(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return high > (long) low * MAX_RANGE_RATIO ? null : (low + high) / 2;
    }

    private List<Integer> extractIntegers(String text) {
        List<Integer> integers = new ArrayList<>();
        Matcher matcher = INTEGER_PATTERN.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group();
            if (digits.length() <= MAX_DIGITS) {
                integers.add(Integer.parseInt(digits));
            }
        }
        return integers;
    }
}
