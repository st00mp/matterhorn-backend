package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.QuoteRequest.Status.TO_VALIDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.st00mp.matterhorn.entity.AgentInstance;
import com.st00mp.matterhorn.entity.ExtractedVariable;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.entity.QuoteLine;
import com.st00mp.matterhorn.entity.QuoteRequest;
import com.st00mp.matterhorn.repository.AgentInstanceRepository;
import com.st00mp.matterhorn.repository.ExtractedVariableRepository;
import com.st00mp.matterhorn.repository.QuoteLineRepository;
import com.st00mp.matterhorn.repository.QuoteRequestRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
class PricingServiceTest {

    /** Euro amounts as the accountant types them in the instance form. */
    private static final Map<String, String> GRID_VALUES = Map.of(
            "base_fee_company", "220.00",
            "base_fee_individual", "135.00",
            "included_documents", "15",
            "price_per_document", "1.20",
            "monthly_vat_surcharge", "36.00");

    @Autowired
    private PricingService pricingService;

    @Autowired
    private AgentInstanceRepository agentInstanceRepository;

    @Autowired
    private QuoteRequestRepository quoteRequestRepository;

    @Autowired
    private ExtractedVariableRepository extractedVariableRepository;

    @Autowired
    private QuoteLineRepository quoteLineRepository;

    /** The mail is never persisted here: pricing only reads the instance it points at. */
    private IncomingMail mailBackedBy(Map<String, String> gridValues) {
        var instance = new AgentInstance();
        instance.setTemplateId(1L);
        instance.setValues(gridValues);

        var mail = new IncomingMail();
        mail.setInstanceId(agentInstanceRepository.save(instance).getId());
        mail.setSender("jean.martin@atelier-lumen.fr");
        mail.setSubject("Question sur vos délais de traitement");
        mail.setBody("Bonjour, je gère une société. Nous traitons 60 factures par mois. TVA mensuelle.");
        return mail;
    }

    private QuoteRequest quoteRequestWith(List<ExtractedVariable> variables) {
        var quoteRequest = new QuoteRequest();
        quoteRequest.setIncomingMailId(1L);
        quoteRequest.setStatus(TO_VALIDATE);
        QuoteRequest saved = quoteRequestRepository.save(quoteRequest);

        variables.forEach(v -> v.setQuoteRequestId(saved.getId()));
        extractedVariableRepository.saveAll(variables);
        return saved;
    }

    private static ExtractedVariable sourced(String key, String value, String citation) {
        var v = new ExtractedVariable();
        v.setKey(key);
        v.setValue(value);
        v.setCitation(citation);
        v.setProvenance(ExtractedVariable.Provenance.SOURCED);
        return v;
    }

    private static List<ExtractedVariable> completeVariables() {
        return List.of(
                sourced("client_legal_form", "COMPANY", "je gère une société"),
                sourced("client_document_volume", "60 factures par mois", "60 factures par mois"),
                sourced("client_vat_regime", "MONTHLY", "TVA mensuelle"));
    }

    @Test
    void price_nominal_persistsLinesAttachedToTheQuoteRequest() {
        // Given
        var quoteRequest = quoteRequestWith(completeVariables());
        var mail = mailBackedBy(GRID_VALUES);

        // When
        var lines = pricingService.price(quoteRequest, mail);

        // Then — euro amounts of the grid, read back as cents
        assertThat(lines).extracting(QuoteLine::getAmountCents).containsExactly(22000L, 5400L, 3600L);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.getId()).isNotNull();
            assertThat(line.getQuoteRequestId()).isEqualTo(quoteRequest.getId());
        });
        assertThat(quoteLineRepository.findAll()).hasSize(3);
    }

    @Test
    void price_unusableVariable_persistsNothing() {
        // Given
        var quoteRequest = quoteRequestWith(List.of(
                sourced("client_legal_form", "COMPANY", "je gère une société"),
                sourced("client_document_volume", "60 factures par mois", "60 factures par mois")));
        var mail = mailBackedBy(GRID_VALUES);

        // When
        var lines = pricingService.price(quoteRequest, mail);

        // Then
        assertThat(lines).isEmpty();
        assertThat(quoteLineRepository.count()).isZero();
    }

    @Test
    void price_gridAmountNotParseable_throwsWithoutPersistingLines() {
        // Given
        var quoteRequest = quoteRequestWith(completeVariables());
        var gridValues = new HashMap<>(GRID_VALUES);
        gridValues.put("base_fee_company", "220,00"); // comma instead of dot
        var mail = mailBackedBy(gridValues);

        // When / Then — the caller decides what to do with an unusable grid
        assertThatThrownBy(() -> pricingService.price(quoteRequest, mail)).isInstanceOf(NumberFormatException.class);
        assertThat(quoteLineRepository.count()).isZero();
    }
}
