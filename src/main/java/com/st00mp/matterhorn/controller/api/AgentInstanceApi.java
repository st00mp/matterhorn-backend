package com.st00mp.matterhorn.controller.api;

import com.st00mp.matterhorn.dto.InstanceRequest;
import com.st00mp.matterhorn.entity.AgentInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI documentation of the agent instance endpoints.
 * <p>
 * Holds only OpenAPI annotations; routing, binding and validation stay on
 * {@link com.st00mp.matterhorn.controller.AgentInstanceController}.
 */
@Tag(name = "Agent Instances", description = "Create agent instances from templates and assemble their output")
public interface AgentInstanceApi {

    @Operation(
            summary = "Create an agent instance",
            description =
                    "Creates a new agent instance from the template identified by templateId and returns the created resource with its URL in the Location header. All fields declared as required by the template must be provided with a non-blank value.")
    @ApiResponse(
            responseCode = "201",
            description = "Instance successfully created",
            headers = @Header(name = "Location", description = "URL of the created resource, e.g. /instances/42"),
            content = @Content(schema = @Schema(implementation = AgentInstance.class)))
    ResponseEntity<AgentInstance> createInstance(
            @Parameter(description = "ID of the template to instantiate", example = "42") Long templateId,
            InstanceRequest request);

    @Operation(
            summary = "Get the assembled output of an agent instance",
            description =
                    "Assembles the instructions of the instance's template by replacing each {{placeholder}} with the instance's stored values, and returns the result as plain text.")
    @ApiResponse(
            responseCode = "200",
            description = "Output successfully assembled",
            content =
                    @Content(
                            mediaType = "text/plain",
                            schema =
                                    @Schema(
                                            type = "string",
                                            example = "You are a helpful assistant with a formal tone.")))
    @ApiResponse(
            responseCode = "422",
            description = "One or more placeholders could not be resolved",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = """
                            {"error": "Unresolved placeholders (no value provided): [tone, name]"}""")))
    ResponseEntity<String> getInstanceOutput(@Parameter(description = "ID of the instance", example = "42") Long id);

    @Operation(
            summary = "Get an agent instance by ID",
            description =
                    "Returns the agent instance identified by id, including its template reference and stored field values.")
    @ApiResponse(
            responseCode = "200",
            description = "Instance found",
            content = @Content(schema = @Schema(implementation = AgentInstance.class)))
    ResponseEntity<AgentInstance> getInstanceById(
            @Parameter(description = "ID of the instance", example = "42") Long id);
}
