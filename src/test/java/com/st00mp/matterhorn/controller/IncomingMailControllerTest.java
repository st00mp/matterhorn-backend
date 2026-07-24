package com.st00mp.matterhorn.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.AgentInstance;
import com.st00mp.matterhorn.llm.FakeLlmClient;
import com.st00mp.matterhorn.llm.FakeLlmTestConfig;
import com.st00mp.matterhorn.repository.AgentInstanceRepository;
import com.st00mp.matterhorn.service.IncomingMailService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLlmTestConfig.class)
class IncomingMailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeLlmClient fake;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentInstanceRepository agentInstanceRepository;

    @Autowired
    private IncomingMailService incomingMailService;

    /** No-op extraction by default: without a programmed response the fake returns null and pollutes the logs. */
    @BeforeEach
    void stubLlm() {
        fake.willReturn("[]");
    }

    private Long seedInstance() {
        var instance = new AgentInstance();
        instance.setTemplateId(1L);
        instance.setValues(Map.of("company_name", "Fiduciaire Horizon"));
        return agentInstanceRepository.save(instance).getId();
    }

    @Nested
    public class CreateIncomingMailTests {

        @Test
        void createIncomingMail_nominal_returns201() throws Exception {
            // Given
            var instanceId = seedInstance();

            var request = new IncomingMailRequest(
                    instanceId,
                    "marie.dupont@horizon-consulting.be",
                    "Demande de devis - mission comptable 2026",
                    "Bonjour, nous cherchons un partenaire pour la tenue de notre comptabilité "
                            + "(PME, 12 salariés). Pourriez-vous nous faire parvenir un devis ? "
                            + "Bien cordialement, Marie Dupont");

            // When
            mockMvc.perform(post("/admin/incoming-mails")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))

                    // Then
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.instanceId").value(instanceId))
                    .andExpect(jsonPath("$.sender").value("marie.dupont@horizon-consulting.be"))
                    .andExpect(jsonPath("$.subject").value("Demande de devis - mission comptable 2026"))
                    .andExpect(jsonPath("$.body").value(request.body()));
        }

        @Test
        void createIncomingMail_blankSender_returns400() throws Exception {
            // Given
            var instanceId = seedInstance();

            var request = new IncomingMailRequest(
                    instanceId,
                    "", // sender blank
                    "Demande de devis - mission comptable 2026",
                    "Bonjour, pourriez-vous nous faire parvenir un devis ? Merci.");

            // When
            mockMvc.perform(post("/admin/incoming-mails")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))

                    // Then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.sender").exists());
        }

        @Test
        void createIncomingMail_blankBody_returns400() throws Exception {
            // Given
            var instanceId = seedInstance();

            var request = new IncomingMailRequest(
                    instanceId,
                    "marie.dupont@horizon-consulting.be",
                    "Demande de devis - mission comptable 2026",
                    "" // body blank
                    );

            // When
            mockMvc.perform(post("/admin/incoming-mails")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))

                    // Then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.body").exists());
        }

        @Test
        void createIncomingMail_unknownInstance_returns404() throws Exception {
            // Given
            var request = new IncomingMailRequest(
                    999999L, // no instance with this id
                    "marie.dupont@horizon-consulting.be",
                    "Demande de devis - mission comptable 2026",
                    "Bonjour, pourriez-vous nous faire parvenir un devis ? Merci.");

            // When
            mockMvc.perform(post("/admin/incoming-mails")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))

                    // Then
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    public class GetIncomingMailTests {

        @Test
        void getIncomingMail_nominal_returns200() throws Exception {
            // Given
            var instanceId = seedInstance();

            var mailId = incomingMailService
                    .create(new IncomingMailRequest(
                            instanceId,
                            "jean.martin@atelier-lumen.fr",
                            "Question sur vos délais de traitement",
                            "Bonjour, quel est votre délai moyen pour la clôture d'un exercice ? Merci d'avance."))
                    .getId();

            // When
            mockMvc.perform(get("/admin/incoming-mails/{id}", mailId))

                    // Then
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(mailId))
                    .andExpect(jsonPath("$.instanceId").value(instanceId))
                    .andExpect(jsonPath("$.sender").value("jean.martin@atelier-lumen.fr"))
                    .andExpect(jsonPath("$.subject").value("Question sur vos délais de traitement"))
                    .andExpect(
                            jsonPath("$.body")
                                    .value(
                                            "Bonjour, quel est votre délai moyen pour la clôture d'un exercice ? Merci d'avance."));
        }

        @Test
        void getIncomingMail_unknownId_returns404() throws Exception {
            mockMvc.perform(get("/admin/incoming-mails/{id}", 999999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").exists());
        }
    }
}
