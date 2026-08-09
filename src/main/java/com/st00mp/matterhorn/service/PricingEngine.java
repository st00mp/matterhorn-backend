package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.ExtractedVariable.Provenance.SOURCED;

import com.st00mp.matterhorn.entity.QuoteLine;
import com.st00mp.matterhorn.service.NormalizedVariables.LegalForm;
import com.st00mp.matterhorn.service.NormalizedVariables.NormalizedVariable;
import com.st00mp.matterhorn.service.NormalizedVariables.VatRegime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Deterministic pricing engine: applies the accountant's {@link Grid} to the
 * {@link NormalizedVariables} of a request and produces the quote lines.
 * No LLM is involved at this stage; all amounts are in euro cents.
 */
@Service
public class PricingEngine {

    /**
     * The accountant's pricing grid. All amounts are in euro cents.
     *
     * @param baseFeeCompanyCents      monthly base fee for a company
     * @param baseFeeIndividualCents   monthly base fee for an individual
     * @param includedDocuments        number of documents included in the base fee
     * @param pricePerDocumentCents    price per document beyond the included volume
     * @param monthlyVatSurchargeCents surcharge for monthly VAT declarations
     */
    public record Grid(
            long baseFeeCompanyCents,
            long baseFeeIndividualCents,
            int includedDocuments,
            long pricePerDocumentCents,
            long monthlyVatSurchargeCents) {}

    /**
     * Prices a request against the grid.
     *
     * <p>Golden rule: a single unusable variable → no amount, neither computed
     * nor communicated — the quote comes back empty and a human follows up.
     *
     * <p>Otherwise the quote always contains the base fee and a VAT line
     * (quarterly = 0,00, deliberately visible as included), plus an overflow
     * line when the monthly volume exceeds what the base fee includes.
     */
    public List<QuoteLine> price(Grid grid, NormalizedVariables vars) {
        if (vars.legalForm().isMissing()
                || vars.volume().isMissing()
                || vars.vatRegime().isMissing()) {
            return List.of();
        }

        List<QuoteLine> lines = new ArrayList<>();
        lines.add(baseFeeLine(grid, vars.legalForm()));

        int exceeded = vars.volume().value() - grid.includedDocuments();
        if (exceeded > 0) {
            lines.add(overflowLine(grid, vars.volume(), exceeded));
        }

        lines.add(vatLine(grid, vars.vatRegime()));
        return lines;
    }

    private QuoteLine baseFeeLine(Grid grid, NormalizedVariable<LegalForm> legalForm) {
        boolean company = legalForm.value() == LegalForm.COMPANY;
        // the amount comes straight from the grid; ASSUMED only flags a guessed legal form
        return line(
                company ? "Forfait de base — société" : "Forfait de base — indépendant",
                company ? grid.baseFeeCompanyCents() : grid.baseFeeIndividualCents(),
                legalForm.provenance() == SOURCED ? QuoteLine.Provenance.GRID : QuoteLine.Provenance.ASSUMED,
                null);
    }

    private QuoteLine overflowLine(Grid grid, NormalizedVariable<Integer> volume, int exceeded) {
        boolean sourced = volume.provenance() == SOURCED;
        return line(
                exceeded + " pièces au-delà du forfait × " + formatEuros(grid.pricePerDocumentCents()) + " €",
                exceeded * grid.pricePerDocumentCents(),
                sourced ? QuoteLine.Provenance.DETECTED : QuoteLine.Provenance.ASSUMED,
                sourced ? volume.citation() : null);
    }

    private QuoteLine vatLine(Grid grid, NormalizedVariable<VatRegime> vatRegime) {
        boolean monthly = vatRegime.value() == VatRegime.MONTHLY;
        boolean sourced = vatRegime.provenance() == SOURCED;
        return line(
                monthly ? "Supplément déclarations TVA mensuelles" : "Déclarations TVA trimestrielles",
                monthly ? grid.monthlyVatSurchargeCents() : 0L,
                sourced ? QuoteLine.Provenance.DETECTED : QuoteLine.Provenance.ASSUMED,
                sourced ? vatRegime.citation() : null);
    }

    private static QuoteLine line(String label, long amountCents, QuoteLine.Provenance provenance, String citation) {
        QuoteLine line = new QuoteLine();
        line.setLabel(label);
        line.setAmountCents(amountCents);
        line.setProvenance(provenance);
        line.setCitation(citation);
        return line;
    }

    /** Formats cents as a French decimal amount, e.g. 120 → "1,20". */
    private static String formatEuros(long cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
