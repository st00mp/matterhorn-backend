package com.st00mp.matterhorn.service;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.entity.QuoteRequest;
import com.st00mp.matterhorn.exception.IncomingMailNotFoundException;
import com.st00mp.matterhorn.exception.InstanceNotFoundException;
import com.st00mp.matterhorn.repository.IncomingMailRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class IncomingMailService {

    private final IncomingMailRepository incomingMailRepository;
    private final AgentInstanceService agentInstanceService;
    private final ClassificationService classificationService;
    private final ExtractionService extractionService;

    public IncomingMailService(
            IncomingMailRepository incomingMailRepository,
            AgentInstanceService agentInstanceService,
            ClassificationService classificationService,
            ExtractionService extractionService) {
        this.incomingMailRepository = incomingMailRepository;
        this.agentInstanceService = agentInstanceService;
        this.classificationService = classificationService;
        this.extractionService = extractionService;
    }

    /**
     * Creates and persists a new {@link IncomingMail} attached to an agent instance.
     * <p>
     * The referenced instance is loaded first to guarantee it exists; the mail body
     * is stored verbatim ("as received") and {@code receivedAt} is set by the server
     * at persistence time. Once saved, the mail is passed to the classification
     * service, which creates a linked {@link QuoteRequest}, then to the extraction
     * service, which persists the prospect variables found in the mail body.
     * <p>
     * Extraction failures (LLM unreachable, unparseable answer) are logged but never
     * block the capture: the mail and its {@link QuoteRequest} are persisted anyway.
     *
     * @param request the request holding the instance reference and the mail content
     * @return the newly created and persisted {@link IncomingMail}
     * @throws InstanceNotFoundException if no instance matches the given instanceId
     */
    @Transactional
    public IncomingMail create(IncomingMailRequest request) {

        agentInstanceService.getById(request.instanceId());

        IncomingMail mail = new IncomingMail();

        mail.setInstanceId(request.instanceId());
        mail.setSender(request.sender());
        mail.setSubject(request.subject());
        mail.setBody(request.body());

        IncomingMail savedMail = incomingMailRepository.save(mail);

        QuoteRequest quoteRequest = classificationService.classify(savedMail);

        try {
            extractionService.extract(quoteRequest, savedMail);
        } catch (RuntimeException e) {
            log.error("Extraction failed for mail {}", savedMail.getId(), e);
        }

        return savedMail;
    }

    /**
     * Retrieves an existing incoming mail by its identifier.
     *
     * @param incomingMailId the identifier of the incoming mail to retrieve
     * @return the matching {@link IncomingMail}
     * @throws IncomingMailNotFoundException if no incoming mail matches incomingMailId
     */
    public IncomingMail getById(Long incomingMailId) {
        return incomingMailRepository
                .findById(incomingMailId)
                .orElseThrow(() -> new IncomingMailNotFoundException(incomingMailId));
    }
}
