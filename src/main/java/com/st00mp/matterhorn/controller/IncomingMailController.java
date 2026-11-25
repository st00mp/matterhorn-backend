package com.st00mp.matterhorn.controller;

import com.st00mp.matterhorn.controller.api.IncomingMailApi;
import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.service.IncomingMailService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/admin/incoming-mails")
public class IncomingMailController implements IncomingMailApi {

    private final IncomingMailService incomingMailService;

    public IncomingMailController(IncomingMailService incomingMailService) {
        this.incomingMailService = incomingMailService;
    }

    @Override
    @PostMapping
    public ResponseEntity<IncomingMail> createIncomingMail(@Valid @RequestBody IncomingMailRequest request) {

        IncomingMail saved = incomingMailService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();

        return ResponseEntity.created(location).body(saved);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<IncomingMail> getIncomingMailById(@PathVariable Long id) {
        IncomingMail mail = incomingMailService.getById(id);
        return ResponseEntity.ok(mail);
    }
}
