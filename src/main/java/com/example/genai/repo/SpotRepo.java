package com.example.genai.repo;

import com.example.genai.entity.City;
import com.example.genai.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpotRepo extends JpaRepository<Spot, Long> {
    Optional<Spot> findByCityAndNameIgnoreCase(City city, String name);
}
