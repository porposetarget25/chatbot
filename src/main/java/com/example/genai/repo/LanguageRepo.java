package com.example.genai.repo;

import com.example.genai.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LanguageRepo extends JpaRepository<Language, Long> {
    Optional<Language> findByNameIgnoreCase(String name);
    Optional<Language> findByCode(String code);
}
