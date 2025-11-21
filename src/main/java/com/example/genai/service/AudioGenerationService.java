package com.example.genai.service;
import com.example.genai.entity.*;
import com.example.genai.llm.OpenAIClient;
import com.example.genai.llm.TtsResult;
import com.example.genai.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class AudioGenerationService {

    private final ResponseRepo responseRepo;
    private final OpenAIClient openAIClient;

    private final Path outputDir = Paths.get("C:\\Users\\mohahama\\Downloads\\World-Traveler\\generated_audio");

    @Autowired
    public AudioGenerationService(ResponseRepo responseRepo, OpenAIClient openAIClient) {
        this.responseRepo = responseRepo;
        this.openAIClient = openAIClient;
    }

    @Transactional
    public void generateAllAudio() throws IOException {

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        List<Response> responses = responseRepo.findAll();

        for (Response r : responses) {

            String filename = r.getSpot().getName().replace(" ", "_")
                    + "_" + r.getLanguage().getName().replace(" ", "_")
                    + ".mp3";

            Path file = outputDir.resolve(filename);

            // avoid regenerating if file exists
            if (Files.exists(file)) {
                System.out.println("Already exists → " + filename);
                continue;
            }

            System.out.println("Generating TTS → " + filename);

            TtsResult result = openAIClient
                    .textToSpeech(r.getContent(), "nova")  // your chosen voice
                    .block();

            Files.write(file, result.getAudio());

            // ✅ AUDIO tokens only
            r.setAudioTokensIn(result.getPromptTokens());
            r.setAudioTokensOut(result.getCompletionTokens());
            responseRepo.save(r);
            System.out.println(
                    "AUDIO TOKENS for " + r.getSpot().getName() + " | " + r.getLanguage().getName() +
                            " → IN=" + result.getPromptTokens() +
                            " OUT=" + result.getCompletionTokens()
            );

            System.out.println("Saved file → " + file);
        }
    }
}

