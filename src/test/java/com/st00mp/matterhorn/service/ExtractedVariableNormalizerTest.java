package com.st00mp.matterhorn.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.st00mp.matterhorn.entity.ExtractedVariable;
import com.st00mp.matterhorn.service.NormalizedVariables.LegalForm;
import com.st00mp.matterhorn.service.NormalizedVariables.NormalizedVariable;
import com.st00mp.matterhorn.service.NormalizedVariables.VatRegime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractedVariableNormalizerTest {

    private final ExtractedVariableNormalizer normalizer = new ExtractedVariableNormalizer();

    private static ExtractedVariable variable(
            String key, String value, String citation, ExtractedVariable.Provenance provenance) {
        var v = new ExtractedVariable();
        v.setKey(key);
        v.setValue(value);
        v.setCitation(citation);
        v.setProvenance(provenance);
        return v;
    }

    private static void assertMissing(NormalizedVariable<?> variable) {
        assertThat(variable.isMissing()).isTrue();
        assertThat(variable.value()).isNull();
        assertThat(variable.citation()).isNull();
        assertThat(variable.provenance()).isNull();
    }

    @Test
    void normalize_variableAbsent_allMissing() {
        // When
        var result = normalizer.normalize(List.of());

        // Then
        assertMissing(result.legalForm());
        assertMissing(result.volume());
        assertMissing(result.vatRegime());
    }

    @Test
    void normalize_keyPresentTwice_firstOccurrenceWins() {
        // Given
        var first = variable("client_legal_form", "COMPANY", "Je gère une SRL", ExtractedVariable.Provenance.SOURCED);
        var second = variable("client_legal_form", "INDIVIDUAL", null, ExtractedVariable.Provenance.ASSUMED);

        // When
        var result = normalizer.normalize(List.of(first, second));

        // Then
        assertThat(result.legalForm().value()).isEqualTo(LegalForm.COMPANY);
        assertThat(result.legalForm().citation()).isEqualTo("Je gère une SRL");
    }

    @Nested
    class LegalFormNormalization {

        @Test
        void normalizeLegalForm_nominalCase() {
            // Given
            var v = variable("client_legal_form", "COMPANY", "Je gère une SRL", ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.legalForm().value()).isEqualTo(LegalForm.COMPANY);
            assertThat(result.legalForm().citation()).isEqualTo("Je gère une SRL");
            assertThat(result.legalForm().provenance()).isEqualTo(ExtractedVariable.Provenance.SOURCED);
        }

        @Test
        void normalizeLegalForm_individual() {
            // Given
            var v = variable("client_legal_form", "INDIVIDUAL", null, ExtractedVariable.Provenance.ASSUMED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.legalForm().value()).isEqualTo(LegalForm.INDIVIDUAL);
            assertThat(result.legalForm().provenance()).isEqualTo(ExtractedVariable.Provenance.ASSUMED);
        }

        @Test
        void normalizeLegalForm_valueOutsideEnum_missing() {
            // Given
            var v = variable("client_legal_form", "ASBL", "Je gère une ASBL", ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.legalForm());
        }

        @Test
        void normalizeLegalForm_valueNull_missing() {
            // Given
            var v = variable("client_legal_form", null, null, ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.legalForm());
        }

        @Test
        void normalizeLegalForm_contradictoryProvenanceMissing_missing() {
            // contradictory state (MISSING implies a null value): the pessimistic provenance wins
            // Given
            var v = variable("client_legal_form", "COMPANY", null, ExtractedVariable.Provenance.MISSING);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.legalForm());
        }
    }

    @Nested
    class VolumeNormalization {

        @Test
        void normalizeVolume_sourcedTwoNumbers_lowerMedian() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "environ 13 factures par mois",
                    "entre 10 et 15 factures par mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.volume().value()).isEqualTo(12);
            assertThat(result.volume().citation()).isEqualTo("entre 10 et 15 factures par mois");
            assertThat(result.volume().provenance()).isEqualTo(ExtractedVariable.Provenance.SOURCED);
        }

        @Test
        void normalizeVolume_assumedSingleNumber_usesValue() {
            // Given
            var v = variable(
                    "client_document_volume", "15 factures par mois", null, ExtractedVariable.Provenance.ASSUMED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.volume().value()).isEqualTo(15);
            assertThat(result.volume().citation()).isNull();
            assertThat(result.volume().provenance()).isEqualTo(ExtractedVariable.Provenance.ASSUMED);
        }

        @Test
        void normalizeVolume_mensuelKeyword_accepted() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "un volume mensuel de 20 documents",
                    null,
                    ExtractedVariable.Provenance.ASSUMED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.volume().value()).isEqualTo(20);
        }

        @Test
        void normalizeVolume_yearlyPeriod_missing() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "1200 pièces par an",
                    "1200 pièces par an",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.volume());
        }

        @Test
        void normalizeVolume_noNumberInText_missing() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "beaucoup de factures par mois",
                    "beaucoup de factures par mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.volume());
        }

        @Test
        void normalizeVolume_threeNumbersInText_missing() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "10 factures, 20 notes de frais et 5 extraits par mois",
                    "10 factures, 20 notes de frais et 5 extraits par mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.volume());
        }

        @Test
        void normalizeVolume_strayNumberInCitation_missing() {
            // a year is not a range bound: averaging it would invoice ~1000 documents a month
            // Given
            var v = variable(
                    "client_document_volume",
                    "30 factures par mois",
                    "depuis 2020, nous traitons 30 factures par mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.volume());
        }

        @Test
        void normalizeVolume_widestAcceptedRange_lowerMedian() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "entre 15 et 150 pièces par mois", // ratio 10, the widest range still read as one
                    "entre 15 et 150 pièces par mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.volume().value()).isEqualTo(82);
        }

        @Test
        void normalizeVolume_numberTooLargeForDocumentCount_missing() {
            // Given
            var v = variable(
                    "client_document_volume",
                    "12345678901 factures par mois",
                    "12345678901 factures par mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.volume());
        }

        @Test
        void normalizeVolume_sourcedWithoutCitation_missing() {
            // Given
            var v = variable(
                    "client_document_volume", "15 factures par mois", null, ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.volume());
        }
    }

    @Nested
    class VatRegimeNormalization {

        @Test
        void normalizeVatRegime_nominalCase() {
            // Given
            var v = variable(
                    "client_vat_regime",
                    "MONTHLY",
                    "Je déclare la TVA tous les mois",
                    ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.vatRegime().value()).isEqualTo(VatRegime.MONTHLY);
            assertThat(result.vatRegime().citation()).isEqualTo("Je déclare la TVA tous les mois");
            assertThat(result.vatRegime().provenance()).isEqualTo(ExtractedVariable.Provenance.SOURCED);
        }

        @Test
        void normalizeVatRegime_quarterly() {
            // Given
            var v = variable("client_vat_regime", "QUARTERLY", null, ExtractedVariable.Provenance.ASSUMED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertThat(result.vatRegime().value()).isEqualTo(VatRegime.QUARTERLY);
            assertThat(result.vatRegime().provenance()).isEqualTo(ExtractedVariable.Provenance.ASSUMED);
        }

        @Test
        void normalizeVatRegime_valueOutsideEnum_missing() {
            // Given
            var v = variable("client_vat_regime", "YEARLY", "TVA annuelle", ExtractedVariable.Provenance.SOURCED);

            // When
            var result = normalizer.normalize(List.of(v));

            // Then
            assertMissing(result.vatRegime());
        }
    }
}
