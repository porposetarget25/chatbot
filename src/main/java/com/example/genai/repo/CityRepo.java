package com.example.genai.repo;

import com.example.genai.entity.City;
import com.example.genai.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepo extends JpaRepository<City, Long> {
    Optional<City> findByCountryAndNameIgnoreCase(Country c, String name);
}
