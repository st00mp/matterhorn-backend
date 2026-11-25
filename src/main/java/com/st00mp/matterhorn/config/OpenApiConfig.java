package com.st00mp.matterhorn.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI information displayed at the top of Swagger UI.
 * Endpoint-level documentation lives on the controller.api interfaces; common error
 * behavior (400/404) is handled by the GlobalExceptionHandler.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI matterhornOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Matterhorn API")
                        .version("v1")
                        .description("""
                                REST API to manage agent templates and their instances. \
                                Templates hold reusable instructions with {{placeholders}}; \
                                instances bind values to those placeholders and assemble the final output.""")
                        .license(new License().name("Proprietary — All rights reserved"))
                        .contact(new Contact().name("Matterhorn").url("https://github.com/st00mp/matterhorn-backend")));
    }
}
