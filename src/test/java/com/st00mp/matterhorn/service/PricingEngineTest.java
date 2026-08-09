package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.ExtractedVariable.Provenance.ASSUMED;
import static com.st00mp.matterhorn.entity.ExtractedVariable.Provenance.SOURCED;
import static org.assertj.core.api.Assertions.assertThat;

import com.st00mp.matterhorn.entity.QuoteLine;
import com.st00mp.matterhorn.service.NormalizedVariables.LegalForm;
import com.st00mp.matterhorn.service.NormalizedVariables.NormalizedVariable;
import com.st00mp.matterhorn.service.NormalizedVariables.VatRegime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PricingEngineTest {

    private static final PricingEngine.Grid GRID = new PricingEngine.Grid(22000, 13500, 15, 120, 3600);

    private final PricingEngine engine = new PricingEngine();

    private static <T> NormalizedVariable<T> sourced(T value, String citation) {
        return new NormalizedVariable<>(value, SOURCED, citation);
    }

    private static <T> NormalizedVariable<T> assumed(T value) {
        return new NormalizedVariable<>(value, ASSUMED, null);
    }

    private static void assertLine(
            QuoteLine line, String label, long amountCents, QuoteLine.Provenance provenance, String citation) {
        assertThat(line.getLabel()).isEqualTo(label);
        assertThat(line.getAmountCents()).isEqualTo(amountCents);
        assertThat(line.getProvenance()).isEqualTo(provenance);
        assertThat(line.getCitation()).isEqualTo(citation);
    }

    @Test
    void price_companyOverVolumeMonthlyVat_threeLines() {
        // Given
        var vars = new NormalizedVariables(
                sourced(LegalForm.COMPANY, "Je gère une SRL"),
                sourced(60, "60 factures par mois"),
                sourced(VatRegime.MONTHLY, "TVA mensuelle"));

        // When
        var lines = engine.price(GRID, vars);

        // Then
        assertThat(lines).hasSize(3);
        assertLine(lines.get(0), "Forfait de base — société", 22000, QuoteLine.Provenance.GRID, null);
        assertLine(
                lines.get(1),
                "45 pièces au-delà du forfait × 1,20 €",
                5400,
                QuoteLine.Provenance.DETECTED,
                "60 factures par mois");
        assertLine(
                lines.get(2),
                "Supplément déclarations TVA mensuelles",
                3600,
                QuoteLine.Provenance.DETECTED,
                "TVA mensuelle");
    }

    @Test
    void price_volumeUnderIncluded_noOverflowLine() {
        // Given
        var vars = new NormalizedVariables(
                sourced(LegalForm.COMPANY, "Je gère une SRL"),
                sourced(12, "12 factures mensuelles"),
                sourced(VatRegime.QUARTERLY, "TVA tous les 3 mois"));

        // When
        var lines = engine.price(GRID, vars);

        // Then
        assertThat(lines).hasSize(2);
        assertLine(lines.get(0), "Forfait de base — société", 22000, QuoteLine.Provenance.GRID, null);
        assertLine(
                lines.get(1),
                "Déclarations TVA trimestrielles",
                0,
                QuoteLine.Provenance.DETECTED,
                "TVA tous les 3 mois");
    }

    @Test
    void price_individualOverVolume_individualBaseFee() {
        // Given
        var vars = new NormalizedVariables(
                sourced(LegalForm.INDIVIDUAL, "Je suis à mon compte"),
                sourced(20, "20 factures par mois"),
                sourced(VatRegime.QUARTERLY, "TVA tous les 3 mois"));

        // When
        var lines = engine.price(GRID, vars);

        // Then
        assertThat(lines).hasSize(3);
        assertLine(lines.get(0), "Forfait de base — indépendant", 13500, QuoteLine.Provenance.GRID, null);
        assertLine(
                lines.get(1),
                "5 pièces au-delà du forfait × 1,20 €",
                600,
                QuoteLine.Provenance.DETECTED,
                "20 factures par mois");
        assertLine(
                lines.get(2),
                "Déclarations TVA trimestrielles",
                0,
                QuoteLine.Provenance.DETECTED,
                "TVA tous les 3 mois");
    }

    @Test
    void price_assumedLegalForm_baseFeeAssumedOthersDetected() {
        // Given
        var vars = new NormalizedVariables(
                assumed(LegalForm.COMPANY),
                sourced(60, "60 factures par mois"),
                sourced(VatRegime.MONTHLY, "TVA tous les mois"));

        // When
        var lines = engine.price(GRID, vars);

        // Then
        assertThat(lines).hasSize(3);
        assertLine(lines.get(0), "Forfait de base — société", 22000, QuoteLine.Provenance.ASSUMED, null);
        assertLine(
                lines.get(1),
                "45 pièces au-delà du forfait × 1,20 €",
                5400,
                QuoteLine.Provenance.DETECTED,
                "60 factures par mois");
        assertLine(
                lines.get(2),
                "Supplément déclarations TVA mensuelles",
                3600,
                QuoteLine.Provenance.DETECTED,
                "TVA tous les mois");
    }

    @Test
    void price_sameInputsTwice_identicalLines() {
        // Given
        var vars = new NormalizedVariables(
                sourced(LegalForm.COMPANY, "Je gère une SRL"),
                sourced(60, "60 factures par mois"),
                sourced(VatRegime.MONTHLY, "TVA mensuelle"));

        // When
        var lines1 = engine.price(GRID, vars);
        var lines2 = engine.price(GRID, vars);

        // Then
        assertThat(lines1).usingRecursiveFieldByFieldElementComparator().isEqualTo(lines2);
    }

    @Test
    void price_volumeExactlyIncluded_noOverflowLine() {
        // Given
        var vars = new NormalizedVariables(
                sourced(LegalForm.COMPANY, "Je gère une SRL"),
                sourced(15, "15 factures par mois"), // exactly the included volume
                sourced(VatRegime.QUARTERLY, "TVA tous les 3 mois"));

        // When
        var lines = engine.price(GRID, vars);

        // Then
        assertThat(lines).hasSize(2);
        assertLine(lines.get(0), "Forfait de base — société", 22000, QuoteLine.Provenance.GRID, null);
        assertLine(
                lines.get(1),
                "Déclarations TVA trimestrielles",
                0,
                QuoteLine.Provenance.DETECTED,
                "TVA tous les 3 mois");
    }

    private static Stream<Arguments> oneMissingVariable() {
        var legalForm = sourced(LegalForm.COMPANY, "Je gère une SRL");
        var volume = sourced(60, "60 factures par mois");
        var vatRegime = sourced(VatRegime.MONTHLY, "TVA mensuelle");
        return Stream.of(
                Arguments.of("legalForm", new NormalizedVariables(NormalizedVariable.missing(), volume, vatRegime)),
                Arguments.of("volume", new NormalizedVariables(legalForm, NormalizedVariable.missing(), vatRegime)),
                Arguments.of("vatRegime", new NormalizedVariables(legalForm, volume, NormalizedVariable.missing())));
    }

    @ParameterizedTest(name = "{0} missing")
    @MethodSource("oneMissingVariable")
    void price_anyVariableMissing_noLines(String missingVariable, NormalizedVariables vars) {
        // When
        var lines = engine.price(GRID, vars);

        // Then
        assertThat(lines).isEmpty();
    }
}
