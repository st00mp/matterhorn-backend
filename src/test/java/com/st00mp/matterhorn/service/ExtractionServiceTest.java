package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.ExtractedVariable.Provenance.*;
import static com.st00mp.matterhorn.entity.QuoteRequest.Status.TO_VALIDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st00mp.matterhorn.entity.ExtractedVariable;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.entity.QuoteRequest;
import com.st00mp.matterhorn.llm.FakeLlmClient;
import com.st00mp.matterhorn.llm.FakeLlmTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;

@Transactional
@SpringBootTest
@Import(FakeLlmTestConfig.class)
class ExtractionServiceTest {

    private static final String BODY = """
        Bonjour, je gère une SRL de construction avec deux associés. \
        Nous traitons environ 150 factures par mois. \
        Nous sommes en dépôt trimestriel de TVA. \
        Pourriez-vous nous faire parvenir un devis ? Bien cordialement, Marc Dubois\
        """;

    @Autowired
    private FakeLlmClient fake;

    @Autowired
    private ExtractionService extractionService;

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    private IncomingMail mailWithBody(String body) {
        var mail = new IncomingMail();
        mail.setId(1L);
        mail.setInstanceId(1L);
        mail.setSender("jean.martin@atelier-lumen.fr");
        mail.setSubject("Question sur vos délais de traitement");
        mail.setBody(body);
        return mail;
    }

    private QuoteRequest quoteRequest(Long id) {
        var quoteRequest = new QuoteRequest();
        quoteRequest.setId(id);
        quoteRequest.setIncomingMailId(1L);
        quoteRequest.setStatus(TO_VALIDATE);
        return quoteRequest;
    }

    @Test
    void extract_sentPrompt_containsMailBody() {
        // Given
        fake.willReturn("[]");

        // When
        extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

        // Then
        assertThat(fake.lastPrompt()).contains(BODY);
    }

    @Nested
    class ProvenanceTests {

        @Test
        void extract_exactQuotes_allVariablesSourced() {
            // Given
            fake.willReturn("""
                [
                  {"key": "client_legal_form", "value": "SRL", "quote": "je gère une SRL"},
                  {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": "environ 150 factures par mois"},
                  {"key": "client_vat_regime", "value": "dépôt trimestriel", "quote": "dépôt trimestriel de TVA"}
                ]
                """);

            // When
            var results = extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

            // Then
            assertThat(results).hasSize(3);
            assertThat(results).allSatisfy(v -> {
                assertThat(v.getId()).isNotNull();
                assertThat(v.getQuoteRequestId()).isEqualTo(1L);
                assertThat(v.getProvenance()).isEqualTo(SOURCED);
            });
            assertThat(results)
                    .extracting(ExtractedVariable::getKey)
                    .containsExactly("client_legal_form", "client_document_volume", "client_vat_regime");
            assertThat(results)
                    .extracting(ExtractedVariable::getValue)
                    .containsExactly("SRL", "environ 150 factures par mois", "dépôt trimestriel");
            assertThat(results)
                    .extracting(ExtractedVariable::getCitation)
                    .containsExactly("je gère une SRL", "environ 150 factures par mois", "dépôt trimestriel de TVA");
        }

        @Test
        void extract_inventedQuote_variableAssumed() {
            // Given
            fake.willReturn("""
                [
                  {"key": "client_legal_form", "value": "SRL", "quote": "je dirige une SRL"},
                  {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": "environ 150 factures par mois"},
                  {"key": "client_vat_regime", "value": "dépôt trimestriel", "quote": "dépôt trimestriel de TVA"}
                ]
                """);

            // When
            var results = extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

            // Then
            assertThat(results).extracting(ExtractedVariable::getProvenance).containsExactly(ASSUMED, SOURCED, SOURCED);
        }

        @Test
        void extract_missingQuote_variableAssumed() {
            // Given
            fake.willReturn("""
                [
                  {"key": "client_legal_form", "value": "SRL", "quote": ""},
                  {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": null},
                  {"key": "client_vat_regime", "value": "dépôt trimestriel", "quote": "dépôt trimestriel de TVA"}
                ]
                """);

            // When
            var results = extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

            // Then
            assertThat(results).extracting(ExtractedVariable::getProvenance).containsExactly(ASSUMED, ASSUMED, SOURCED);
        }

        @Test
        void extract_nullValue_variableMissing() {
            // Given
            fake.willReturn("""
                [
                  {"key": "client_legal_form", "value": null, "quote": null},
                  {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": "environ 150 factures par mois"},
                  {"key": "client_vat_regime", "value": "dépôt trimestriel", "quote": "dépôt trimestriel de TVA"}
                ]
                """);

            // When
            var results = extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

            // Then
            assertThat(results).extracting(ExtractedVariable::getProvenance).containsExactly(MISSING, SOURCED, SOURCED);
            assertThat(results)
                    .extracting(ExtractedVariable::getValue)
                    .containsExactly(null, "environ 150 factures par mois", "dépôt trimestriel");
        }

        @Test
        void extract_emptyValue_variableMissing() {
            // Given
            fake.willReturn("""
                [
                  {"key": "client_legal_form", "value": "", "quote": null},
                  {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": "environ 150 factures par mois"},
                  {"key": "client_vat_regime", "value": "dépôt trimestriel", "quote": "dépôt trimestriel de TVA"}
                ]
                """);

            // When
            var results = extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

            // Then
            assertThat(results).extracting(ExtractedVariable::getProvenance).containsExactly(MISSING, SOURCED, SOURCED);
            assertThat(results)
                    .extracting(ExtractedVariable::getValue)
                    .containsExactly(null, "environ 150 factures par mois", "dépôt trimestriel");
        }
    }

    @Nested
    class ParsingTests {

        @Test
        void extract_fencedResponse_stillParsed() {
            // Given
            fake.willReturn("""
                ```json
                    [
                      {"key": "client_legal_form", "value": "SRL", "quote": "je gère une SRL"},
                      {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": "environ 150 factures par mois"},
                      {"key": "client_vat_regime", "value": "dépôt trimestriel", "quote": "dépôt trimestriel de TVA"}
                    ]
                ```
            """);

            // When
            var results = extractionService.extract(quoteRequest(1L), mailWithBody(BODY));

            // Then
            assertThat(results).hasSize(3);
            assertThat(results).allSatisfy(v -> {
                assertThat(v.getId()).isNotNull();
                assertThat(v.getQuoteRequestId()).isEqualTo(1L);
                assertThat(v.getProvenance()).isEqualTo(SOURCED);
            });
            assertThat(results)
                    .extracting(ExtractedVariable::getKey)
                    .containsExactly("client_legal_form", "client_document_volume", "client_vat_regime");
            assertThat(results)
                    .extracting(ExtractedVariable::getValue)
                    .containsExactly("SRL", "environ 150 factures par mois", "dépôt trimestriel");
            assertThat(results)
                    .extracting(ExtractedVariable::getCitation)
                    .containsExactly("je gère une SRL", "environ 150 factures par mois", "dépôt trimestriel de TVA");
        }

        @Test
        void extract_invalidJson_throws() {
            // Given
            fake.willReturn("ceci n'est pas du JSON");

            // When / Then
            assertThatThrownBy(() -> extractionService.extract(quoteRequest(1L), mailWithBody(BODY)))
                    .isInstanceOf(JacksonException.class);
        }
    }
}
