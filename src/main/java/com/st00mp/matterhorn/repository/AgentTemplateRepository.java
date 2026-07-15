package com.st00mp.matterhorn.repository;

import com.st00mp.matterhorn.entity.AgentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTemplateRepository extends JpaRepository<AgentTemplate, Long> {
    boolean existsByNameAndVersion(String name, String version);
}
