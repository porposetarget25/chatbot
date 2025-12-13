package com.example.genai.controller;

import com.example.genai.service.GeminiAudioService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gemini/audio")
public class GeminiAudioController {

    private final GeminiAudioService geminiAudioService;

    public GeminiAudioController(GeminiAudioService geminiAudioService) {
        this.geminiAudioService = geminiAudioService;
    }

    @PostMapping("/generate")
    public String generate() throws Exception {
        geminiAudioService.generateAllGeminiAudio();
        return "Gemini TTS (2.5 flash preview) mixed audio generation triggered.";
    }
}
