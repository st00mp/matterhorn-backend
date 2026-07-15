package com.st00mp.matterhorn.controller;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.IncomingMail;
import com.st00mp.matterhorn.service.IncomingMailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(
        name = "Incoming Mails",
        description = "Capture incoming mail addressed to an agent instance and read it back as stored")
@RestController
@RequestMapping("/admin/incoming-mails")
public class IncomingMailController {

    private final IncomingMailService incomingMailService;

    public IncomingMailController(IncomingMailService incomingMailService) {
        this.incomingMailService = incomingMailService;
    }

    @Operation(
            summary = "Capture an incoming mail",
            description =
                    "Stores an incoming mail attached to an existing agent instance, exactly as received. The server sets receivedAt. Returns the created resource with its URL in the Location header.")
    @ApiResponse(
            responseCode = "201",
            description = "Incoming mail successfully captured",
            headers =
                    @Header(
                            name = "Location",
                            description = "URL of the created resource, e.g. /admin/incoming-mails/42"),
            content = @Content(schema = @Schema(implementation = IncomingMail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The referenced agent instance does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = """
                            {"error": "AgentInstance 42 not found"}""")))
    @PostMapping
    public ResponseEntity<IncomingMail> createIncomingMail(@Valid @RequestBody IncomingMailRequest request) {

        IncomingMail saved = incomingMailService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();

        return ResponseEntity.created(location).body(saved);
    }

    @Operation(
            summary = "Get an incoming mail by ID",
            description =
                    "Returns the incoming mail exactly as it was stored, including the server-set receivedAt timestamp.")
    @ApiResponse(
            responseCode = "200",
            description = "Incoming mail found",
            content = @Content(schema = @Schema(implementation = IncomingMail.class)))
    @GetMapping("/{id}")
    public ResponseEntity<IncomingMail> getIncomingMailById(
            @Parameter(description = "ID of the incoming mail", example = "42") @PathVariable Long id) {
        IncomingMail mail = incomingMailService.getById(id);
        return ResponseEntity.ok(mail);
    }
}
