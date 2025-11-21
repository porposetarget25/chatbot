package com.example.genai.controller;

import com.example.genai.service.AudioGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioGenerationService audioService;

    @Autowired
    public AudioController(AudioGenerationService audioService) {
        this.audioService = audioService;
    }

    @PostMapping("/generate")
    public String generate() throws Exception {
        audioService.generateAllAudio();
        return "Audio generation started!";
    }
}

