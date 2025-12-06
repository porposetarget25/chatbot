package com.example.genai.controller;


import com.example.genai.service.AudioMixService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/audio")
public class AudioMixController {

    private final AudioMixService audioMixService = new AudioMixService();

    @GetMapping("/mix")
    public ResponseEntity<Resource> mix() throws Exception {
        Path speech = Paths.get("C:/Users/mohahama/Downloads/World-Traveler/Auckland_Zoo.wav");
        Path bg = Paths.get("C:/Users/mohahama/Downloads/World-Traveler/Calm1.mp3");
        Path output = Paths.get("C:/Users/mohahama/Downloads/World-Traveler/mixed_final.wav");

        Path mixedPath = audioMixService.mixSpeechWithBackground(speech, bg, output);

        Resource resource = new FileSystemResource(mixedPath.toFile());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"mixed_final.wav\"")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(resource);
    }
}
