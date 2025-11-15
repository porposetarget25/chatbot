package com.example.genai.repo;

import com.example.genai.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PromptTemplateRepo extends JpaRepository<PromptTemplate, Long> {
    Optional<PromptTemplate> findByKey(String key);
}
