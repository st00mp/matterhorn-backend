package com.st00mp.matterhorn.config;

import com.st00mp.matterhorn.entity.AgentTemplate;
import com.st00mp.matterhorn.repository.AgentTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Seeds the engagement-letter template on startup from classpath resources.
 * The template definition (JSON) and its instructions (Markdown) live under
 * {@code resources/seed/engagement-letter/}; the two are merged into a single
 * {@link AgentTemplate}. Idempotent: skipped if a template with the same
 * name and version already exists.
 */
@Slf4j
@Component
public class TemplateSeedLoader implements ApplicationRunner {

    private final Resource templateFile;
    private final Resource instructionsFile;
    private final ObjectMapper objectMapper;
    private final AgentTemplateRepository repository;

    public TemplateSeedLoader(
            @Value("classpath:seed/engagement-letter/template.json") Resource templateFile,
            @Value("classpath:seed/engagement-letter/instructions.md") Resource instructionsFile,
            ObjectMapper objectMapper,
            AgentTemplateRepository repository) {
        this.templateFile = templateFile;
        this.instructionsFile = instructionsFile;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String templateJson = templateFile.getContentAsString(UTF_8);
        AgentTemplate agentTemplate = objectMapper.readValue(templateJson, AgentTemplate.class);
        String instructions = instructionsFile.getContentAsString(UTF_8);
        agentTemplate.setInstructions(instructions);

        if (repository.existsByNameAndVersion(agentTemplate.getName(), agentTemplate.getVersion())) {
            log.info("Template {} v{} already seeded, skipping", agentTemplate.getName(), agentTemplate.getVersion());
            return;
        }

        repository.save(agentTemplate);
        log.info("Seeded template {} v{}", agentTemplate.getName(), agentTemplate.getVersion());
    }
}