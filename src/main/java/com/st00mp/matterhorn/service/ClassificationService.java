package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.QuoteRequest.Status.TO_VALIDATE;

import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.entity.QuoteRequest;
import com.st00mp.matterhorn.repository.QuoteRequestRepository;
import org.springframework.stereotype.Service;

@Service
public class ClassificationService {

    private final QuoteRequestRepository quoteRequestRepository;

    public ClassificationService(QuoteRequestRepository quoteRequestRepository) {
        this.quoteRequestRepository = quoteRequestRepository;
    }

    // Stub: always routes to human validation. Replaced by LLM classifier in E2-S2 full version.
    public QuoteRequest classify(IncomingMail incomingMail) {

        QuoteRequest quoteRequest = new QuoteRequest();

        quoteRequest.setIncomingMailId(incomingMail.getId());
        quoteRequest.setStatus(TO_VALIDATE);

        return quoteRequestRepository.save(quoteRequest);
    }
}
