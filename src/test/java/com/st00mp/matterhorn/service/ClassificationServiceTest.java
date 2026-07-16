package com.st00mp.matterhorn.service;

import static com.st00mp.matterhorn.entity.QuoteRequest.Status.TO_VALIDATE;
import static org.junit.jupiter.api.Assertions.*;

import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.repository.QuoteRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
class ClassificationServiceTest {

    @Autowired
    private ClassificationService classificationService;

    @Autowired
    private QuoteRequestRepository quoteRequestRepository;

    private IncomingMail incomingMail(Long id) {
        IncomingMail mail = new IncomingMail();
        mail.setId(id);
        mail.setInstanceId(1L);
        mail.setSender("jean.martin@atelier-lumen.fr");
        mail.setSubject("Question sur vos délais de traitement");
        mail.setBody("Bonjour, quel est votre délai moyen pour la clôture d'un exercice ? Merci d'avance.");
        return mail;
    }

    @Test
    void classify_nominal_persistsQuoteRequestToValidate() {
        // When
        var quoteRequest = classificationService.classify(incomingMail(42L));

        // Then
        assertEquals(1, quoteRequestRepository.count());
        var saved = quoteRequestRepository.findById(quoteRequest.getId()).orElseThrow();
        assertEquals(42L, saved.getIncomingMailId());
        assertEquals(TO_VALIDATE, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void classify_secondMail_createsSecondQuoteRequest() {
        // Given
        var firstQuoteRequest = classificationService.classify(incomingMail(42L));

        // When
        var secondQuoteRequest = classificationService.classify(incomingMail(43L));

        // Then
        assertEquals(2, quoteRequestRepository.count());
        assertNotEquals(firstQuoteRequest.getId(), secondQuoteRequest.getId());
        assertEquals(43L, secondQuoteRequest.getIncomingMailId());
        assertEquals(TO_VALIDATE, secondQuoteRequest.getStatus());
    }
}
