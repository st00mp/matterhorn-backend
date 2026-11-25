package com.st00mp.matterhorn.controller;

import com.st00mp.matterhorn.controller.api.AgentInstanceApi;
import com.st00mp.matterhorn.dto.InstanceRequest;
import com.st00mp.matterhorn.entity.AgentInstance;
import com.st00mp.matterhorn.service.AgentInstanceService;
import com.st00mp.matterhorn.service.AssemblyService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AgentInstanceController implements AgentInstanceApi {

    private final AgentInstanceService agentInstanceService;
    private final AssemblyService assemblyService;

    public AgentInstanceController(AgentInstanceService agentInstanceService, AssemblyService assemblyService) {
        this.agentInstanceService = agentInstanceService;
        this.assemblyService = assemblyService;
    }

    @Override
    @PostMapping("/templates/{templateId}/instances")
    public ResponseEntity<AgentInstance> createInstance(
            @PathVariable Long templateId, @Valid @RequestBody InstanceRequest request) {

        AgentInstance saved = agentInstanceService.create(templateId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/instances/{id}")
                .buildAndExpand(saved.getId())
                .toUri();

        return ResponseEntity.created(location).body(saved);
    }

    @Override
    @GetMapping(value = "/instances/{id}/output", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getInstanceOutput(@PathVariable Long id) {
        String output = assemblyService.assembleOutput(id);
        return ResponseEntity.ok(output);
    }

    @Override
    @GetMapping("/instances/{id}")
    public ResponseEntity<AgentInstance> getInstanceById(@PathVariable Long id) {
        AgentInstance instance = agentInstanceService.getById(id);
        return ResponseEntity.ok(instance);
    }
}
