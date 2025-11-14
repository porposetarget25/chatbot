package com.example.genai.repo;

import com.example.genai.entity.PromptTemplate;
import com.example.genai.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepo extends JpaRepository<PromptTemplate, Long> {
    Optional<PromptTemplate> findBySpotAndPromptKey(Spot spot, String promptKey);
    List<PromptTemplate> findBySpot(Spot spot);
}
