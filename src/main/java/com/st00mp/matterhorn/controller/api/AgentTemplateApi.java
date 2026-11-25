package com.st00mp.matterhorn.controller.api;

import com.st00mp.matterhorn.dto.TemplateRequest;
import com.st00mp.matterhorn.entity.AgentTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI documentation of the agent template endpoints.
 * <p>
 * Holds only OpenAPI annotations; routing, binding and validation stay on
 * {@link com.st00mp.matterhorn.controller.AgentTemplateController}.
 */
@Tag(name = "Agent Templates", description = "Create, read, update and delete reusable agent templates")
public interface AgentTemplateApi {

    @Operation(
            summary = "Create an agent template",
            description =
                    "Creates a new agent template and returns the created resource with its URL in the Location header.")
    @ApiResponse(
            responseCode = "201",
            description = "Template successfully created",
            headers = @Header(name = "Location", description = "URL of the created resource, e.g. /templates/42"),
            content = @Content(schema = @Schema(implementation = AgentTemplate.class)))
    ResponseEntity<AgentTemplate> createTemplate(TemplateRequest request);

    @Operation(summary = "Get an agent template", description = "Returns an agent template by its ID.")
    @ApiResponse(
            responseCode = "200",
            description = "Template successfully retrieved",
            content = @Content(schema = @Schema(implementation = AgentTemplate.class)))
    ResponseEntity<AgentTemplate> getTemplateById(
            @Parameter(description = "ID of the template", example = "42") Long id);

    @Operation(
            summary = "List all agent templates",
            description = "Returns all agent templates. Returns an empty array if no template exists.")
    @ApiResponse(
            responseCode = "200",
            description = "Templates successfully retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AgentTemplate.class))))
    ResponseEntity<List<AgentTemplate>> getAllTemplates();

    @Operation(
            summary = "Update an agent template",
            description = "Updates an existing agent template by its ID and returns the updated resource.")
    @ApiResponse(
            responseCode = "200",
            description = "Template successfully updated",
            content = @Content(schema = @Schema(implementation = AgentTemplate.class)))
    ResponseEntity<AgentTemplate> updateTemplate(
            @Parameter(description = "ID of the template", example = "42") Long id, TemplateRequest request);

    @Operation(summary = "Delete an agent template", description = "Deletes an agent template by its ID.")
    @ApiResponse(responseCode = "204", description = "Template successfully deleted")
    ResponseEntity<Void> deleteTemplate(@Parameter(description = "ID of the template", example = "42") Long id);
}
