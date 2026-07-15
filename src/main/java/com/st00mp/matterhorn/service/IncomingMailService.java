package com.st00mp.matterhorn.service;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.exception.IncomingMailNotFoundException;
import com.st00mp.matterhorn.exception.InstanceNotFoundException;
import com.st00mp.matterhorn.repository.IncomingMailRepository;
import org.springframework.stereotype.Service;

@Service
public class IncomingMailService {

    private final IncomingMailRepository incomingMailRepository;
    private final AgentInstanceService agentInstanceService;

    public IncomingMailService(IncomingMailRepository incomingMailRepository, AgentInstanceService agentInstanceService) {
        this.incomingMailRepository = incomingMailRepository;
        this.agentInstanceService = agentInstanceService;
    }

    /**
     * Creates and persists a new {@link IncomingMail} attached to an agent instance.
     * <p>
     * The referenced instance is loaded first to guarantee it exists; the mail body
     * is stored verbatim ("as received") and {@code receivedAt} is set by the server
     * at persistence time.
     *
     * @param request the request holding the instance reference and the mail content
     * @return the newly created and persisted {@link IncomingMail}
     * @throws InstanceNotFoundException if no instance matches the given instanceId
     */
    public IncomingMail create(IncomingMailRequest request) {

        agentInstanceService.getById(request.instanceId());

        IncomingMail mail = new IncomingMail();

        mail.setInstanceId(request.instanceId());
        mail.setSender(request.sender());
        mail.setSubject(request.subject());
        mail.setBody(request.body());

        return incomingMailRepository.save(mail);
    }

    /**
     * Retrieves an existing incoming mail by its identifier.
     *
     * @param incomingMailId the identifier of the incoming mail to retrieve
     * @return the matching {@link IncomingMail}
     * @throws IncomingMailNotFoundException if no incoming mail matches incomingMailId
     */
    public IncomingMail getById(Long incomingMailId) {
        return incomingMailRepository.findById(incomingMailId)
                .orElseThrow(() -> new IncomingMailNotFoundException(incomingMailId));
    }
}
