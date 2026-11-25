package com.st00mp.matterhorn.controller;

import com.st00mp.matterhorn.controller.api.AgentTemplateApi;
import com.st00mp.matterhorn.dto.TemplateRequest;
import com.st00mp.matterhorn.entity.AgentTemplate;
import com.st00mp.matterhorn.service.AgentTemplateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AgentTemplateController implements AgentTemplateApi {

    private final AgentTemplateService agentTemplateService;

    public AgentTemplateController(AgentTemplateService agentTemplateService) {
        this.agentTemplateService = agentTemplateService;
    }

    @Override
    @PostMapping("/templates")
    public ResponseEntity<AgentTemplate> createTemplate(@Valid @RequestBody TemplateRequest request) {

        AgentTemplate saved = agentTemplateService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();

        return ResponseEntity.created(location).body(saved);
    }

    @Override
    @GetMapping("/templates/{id}")
    public ResponseEntity<AgentTemplate> getTemplateById(@PathVariable Long id) {
        AgentTemplate template = agentTemplateService.getById(id);
        return ResponseEntity.ok(template);
    }

    @Override
    @GetMapping("/templates")
    public ResponseEntity<List<AgentTemplate>> getAllTemplates() {
        List<AgentTemplate> templates = agentTemplateService.getAll();
        return ResponseEntity.ok(templates);
    }

    @Override
    @PutMapping("/templates/{id}")
    public ResponseEntity<AgentTemplate> updateTemplate(
            @PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
        AgentTemplate updated = agentTemplateService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @Override
    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        agentTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
