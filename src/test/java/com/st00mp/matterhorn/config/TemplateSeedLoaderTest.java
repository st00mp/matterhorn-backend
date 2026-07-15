package com.st00mp.matterhorn.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.st00mp.matterhorn.entity.AgentTemplate;
import com.st00mp.matterhorn.repository.AgentTemplateRepository;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

@SpringBootTest
class TemplateSeedLoaderTest {

    @Autowired
    private TemplateSeedLoader loader;

    @Autowired
    private AgentTemplateRepository repository;

    @Value("classpath:seed/engagement-letter/instructions.md")
    private Resource instructionsFile;

    @Test
    void applicationStart_emptyDatabase_seedsTemplate() throws Exception {
        // Then
        assertEquals(1, repository.count());
        AgentTemplate seeded = repository.findAll().getFirst();
        assertEquals("Agent lettre de mission", seeded.getName());
        assertEquals("1", seeded.getVersion());
    }

    @Test
    void applicationStart_existingNameAndVersion_skipsSave() throws Exception {
        // Given: Spring context has already seeded the template
        long countBefore = repository.count();

        // When
        loader.run(null);

        // Then
        assertEquals(countBefore, repository.count());
    }

    @Test
    void applicationStart_seededTemplate_carriesRawInstructions() throws IOException {
        // Given
        String expected = instructionsFile.getContentAsString(UTF_8);

        // When
        AgentTemplate seeded = repository.findAll().getFirst();

        // Then
        assertEquals(expected, seeded.getInstructions());
    }
}
