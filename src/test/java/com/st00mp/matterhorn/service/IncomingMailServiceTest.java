package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.QuoteRequest.Status.TO_VALIDATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.AgentInstance;
import com.st00mp.matterhorn.entity.QuoteLine;
import com.st00mp.matterhorn.llm.FakeLlmClient;
import com.st00mp.matterhorn.llm.FakeLlmTestConfig;
import com.st00mp.matterhorn.repository.*;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@Import(FakeLlmTestConfig.class)
class IncomingMailServiceTest {

    @Autowired
    private FakeLlmClient fake;

    @Autowired
    private AgentInstanceRepository agentInstanceRepository;

    @Autowired
    private IncomingMailRepository incomingMailRepository;

    @Autowired
    private IncomingMailService incomingMailService;

    @Autowired
    private QuoteRequestRepository quoteRequestRepository;

    @Autowired
    private ExtractedVariableRepository extractedVariableRepository;

    @Autowired
    private QuoteLineRepository quoteLineRepository;

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    /** The tests below that commit for real leave rows behind; the others roll back anyway. */
    @AfterEach
    void clearCommittedRows() {
        quoteLineRepository.deleteAll();
        extractedVariableRepository.deleteAll();
        quoteRequestRepository.deleteAll();
        incomingMailRepository.deleteAll();
        agentInstanceRepository.deleteAll();
    }

    private Long seedInstanceId() {
        var instance = new AgentInstance();
        instance.setTemplateId(1L);
        instance.setValues(Map.of(
                "base_fee_company", "220.00",
                "base_fee_individual", "135.00",
                "included_documents", "15",
                "price_per_document", "1.20",
                "monthly_vat_surcharge", "36.00"));
        return agentInstanceRepository.save(instance).getId();
    }

    private Long seedInstanceIdWithFrenchDecimal() {
        var instance = new AgentInstance();
        instance.setTemplateId(1L);
        instance.setValues(Map.of(
                "base_fee_company", "220,00", // comma instead of dot
                "base_fee_individual", "135.00",
                "included_documents", "15",
                "price_per_document", "1.20",
                "monthly_vat_surcharge", "36.00"));
        return agentInstanceRepository.save(instance).getId();
    }

    private IncomingMailRequest requestWithBody(Long instanceId, String body) {
        return new IncomingMailRequest(
                instanceId, "jean.martin@atelier-lumen.fr", "Question sur vos délais de traitement", body);
    }

    @Test
    void create_nominal_persistsMailQuoteRequestAndVariablesAndQuoteLines() {
        // Given
        var request = requestWithBody(
                seedInstanceId(),
                "Bonjour, je gère une société. Nous traitons 60 factures par mois. TVA mensuelle. Un devis svp.");
        fake.willReturn("""
            [
              {"key": "client_legal_form", "value": "COMPANY", "quote": "je gère une société"},
              {"key": "client_document_volume", "value": "60 factures par mois", "quote": "60 factures par mois"},
              {"key": "client_vat_regime", "value": "MONTHLY", "quote": "TVA mensuelle"}
            ]
            """);

        // When
        var savedMail = incomingMailService.create(request);

        // Then
        assertThat(savedMail.getId()).isNotNull();

        var quoteRequests = quoteRequestRepository.findAll();
        assertThat(quoteRequests).hasSize(1);
        assertThat(quoteRequests.getFirst().getIncomingMailId()).isEqualTo(savedMail.getId());

        var variables = extractedVariableRepository.findAll();
        assertThat(variables).hasSize(3);
        assertThat(variables)
                .allSatisfy(v -> assertThat(v.getQuoteRequestId())
                        .isEqualTo(quoteRequests.getFirst().getId()));

        var quotes = quoteLineRepository.findAll();
        assertThat(quotes).hasSize(3);
        assertThat(quotes).extracting(QuoteLine::getAmountCents).containsExactly(22000L, 5400L, 3600L);
    }

    /**
     * Runs outside the test-managed transaction on purpose: "the capture survives"
     * only means something if the transaction actually commits. Inside a rolled-back
     * test transaction, a chain step poisoning it would go unnoticed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_extractionFails_mailStillPersisted() {
        // Given
        var request = requestWithBody(seedInstanceId(), "Bonjour, un devis svp.");
        fake.willThrow(new RuntimeException("LLM down"));

        // When
        var savedMail = incomingMailService.create(request);

        // Then
        assertThat(savedMail.getId()).isNotNull();
        assertThat(incomingMailRepository.count()).isEqualTo(1);
        assertThat(quoteRequestRepository.count()).isEqualTo(1);
        assertThat(extractedVariableRepository.count()).isEqualTo(0);
    }

    @Test
    void create_unusableVariables_persistsRequestWithoutLines() {
        // Given
        var request = requestWithBody(
                seedInstanceId(), "Bonjour, je gère une société. Nous traitons 60 factures par mois. Un devis svp.");
        fake.willReturn("""
            [
              {"key": "client_legal_form", "value": "COMPANY", "quote": "je gère une société"},
              {"key": "client_document_volume", "value": "60 factures par mois", "quote": "60 factures par mois"},
              {"key": "client_vat_regime", "value": null, "quote": null}
            ]
            """);
        // When
        var savedMail = incomingMailService.create(request);

        // Then
        var quoteRequest = quoteRequestRepository.findAll();
        assertThat(quoteRequest).hasSize(1);
        assertThat(quoteRequest.getFirst().getIncomingMailId()).isEqualTo(savedMail.getId());
        assertThat(quoteRequest.getFirst().getStatus()).isEqualTo(TO_VALIDATE);

        assertThat(quoteLineRepository.count()).isZero();
    }

    /** Outside the test-managed transaction, for the reason given above. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_pricingFails_mailAndQuoteRequestStillPersisted() {
        // Given
        var request = requestWithBody(
                seedInstanceIdWithFrenchDecimal(),
                "Bonjour, je gère une société. Nous traitons 60 factures par mois. TVA mensuelle. Un devis svp.");
        fake.willReturn("""
            [
              {"key": "client_legal_form", "value": "COMPANY", "quote": "je gère une société"},
              {"key": "client_document_volume", "value": "60 factures par mois", "quote": "60 factures par mois"},
              {"key": "client_vat_regime", "value": "MONTHLY", "quote": "TVA mensuelle"}
            ]
            """);

        // When
        var savedMail = incomingMailService.create(request);

        // Then
        assertThat(savedMail.getId()).isNotNull();
        assertThat(quoteRequestRepository.count()).isEqualTo(1);
        assertThat(quoteLineRepository.count()).isZero();
    }
}
