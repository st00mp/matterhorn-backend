package com.st00mp.matterhorn.repository;

import com.st00mp.matterhorn.entity.ExtractedVariable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedVariableRepository extends JpaRepository<ExtractedVariable, Long> {
    List<ExtractedVariable> findByQuoteRequestId(Long quoteRequestId);
}
