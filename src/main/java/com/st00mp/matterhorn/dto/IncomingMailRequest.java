package com.st00mp.matterhorn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IncomingMailRequest(

        @NotNull Long instanceId,

        @NotBlank String sender,

        String subject,

        @NotBlank String body

) {
}
