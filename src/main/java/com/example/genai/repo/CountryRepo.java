package com.example.genai.repo;

import com.example.genai.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CountryRepo extends JpaRepository<Country, Long> {
    Optional<Country> findByNameIgnoreCase(String name);
}
