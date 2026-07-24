package com.st00mp.matterhorn.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.AgentInstance;
import com.st00mp.matterhorn.llm.FakeLlmClient;
import com.st00mp.matterhorn.llm.FakeLlmTestConfig;
import com.st00mp.matterhorn.repository.AgentInstanceRepository;
import com.st00mp.matterhorn.repository.ExtractedVariableRepository;
import com.st00mp.matterhorn.repository.IncomingMailRepository;
import com.st00mp.matterhorn.repository.QuoteRequestRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    private Long seedInstanceId() {
        var instance = new AgentInstance();
        instance.setTemplateId(1L);
        instance.setValues(Map.of());
        return agentInstanceRepository.save(instance).getId();
    }

    private IncomingMailRequest requestWithBody(Long instanceId, String body) {
        return new IncomingMailRequest(
                instanceId, "jean.martin@atelier-lumen.fr", "Question sur vos délais de traitement", body);
    }

    @Test
    void create_nominal_persistsMailQuoteRequestAndVariables() {
        // Given
        var request = requestWithBody(
                seedInstanceId(),
                "Bonjour, je gère une SRL. Nous traitons environ 150 factures par mois. Un devis svp.");
        fake.willReturn("""
            [
              {"key": "client_legal_form", "value": "SRL", "quote": "je gère une SRL"},
              {"key": "client_document_volume", "value": "environ 150 factures par mois", "quote": "environ 150 factures par mois"},
              {"key": "client_vat_regime", "value": null, "quote": null}
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
    }

    @Test
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
}
