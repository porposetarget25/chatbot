package com.example.genai.controller;

import com.example.genai.service.TravelGenerationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/generate")
public class TravelGenerationController {

    private final TravelGenerationService travelService;

    public TravelGenerationController(TravelGenerationService travelService) {
        this.travelService = travelService;
    }

    @PostMapping("/travel")
    public String generate() {
        travelService.generateAllTravelDescriptions();
        return "Travel descriptions generated successfully!";
    }
}
