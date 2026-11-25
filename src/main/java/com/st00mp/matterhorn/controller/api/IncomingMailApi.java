package com.st00mp.matterhorn.controller.api;

import com.st00mp.matterhorn.dto.IncomingMailRequest;
import com.st00mp.matterhorn.entity.IncomingMail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI documentation of the incoming mail endpoints.
 * <p>
 * Holds only OpenAPI annotations; routing, binding and validation stay on
 * {@link com.st00mp.matterhorn.controller.IncomingMailController}.
 */
@Tag(
        name = "Incoming Mails",
        description = "Capture incoming mail addressed to an agent instance and read it back as stored")
public interface IncomingMailApi {

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
    ResponseEntity<IncomingMail> createIncomingMail(IncomingMailRequest request);

    @Operation(
            summary = "Get an incoming mail by ID",
            description =
                    "Returns the incoming mail exactly as it was stored, including the server-set receivedAt timestamp.")
    @ApiResponse(
            responseCode = "200",
            description = "Incoming mail found",
            content = @Content(schema = @Schema(implementation = IncomingMail.class)))
    ResponseEntity<IncomingMail> getIncomingMailById(
            @Parameter(description = "ID of the incoming mail", example = "42") Long id);
}
