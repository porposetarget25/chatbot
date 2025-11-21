package com.example.genai.service;

import com.example.genai.entity.*;
import com.example.genai.llm.LLMResult;
import com.example.genai.llm.OpenAIClient;
import com.example.genai.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TravelGenerationService {

    private final SpotRepo spotRepo;
    private final LanguageRepo languageRepo;
    private final PromptTemplateRepo templateRepo;
    private final ResponseRepo responseRepo;
    private final OpenAIClient openAIClient;

    public TravelGenerationService(
            SpotRepo spotRepo,
            LanguageRepo languageRepo,
            PromptTemplateRepo templateRepo,
            ResponseRepo responseRepo,
            OpenAIClient openAIClient
    ) {
        this.spotRepo = spotRepo;
        this.languageRepo = languageRepo;
        this.templateRepo = templateRepo;
        this.responseRepo = responseRepo;
        this.openAIClient = openAIClient;
    }

    @Transactional
    public void generateAllTravelDescriptions() {

        PromptTemplate template = templateRepo.findByKey("TRAVEL_DESC_V1")
                .orElseThrow(() -> new IllegalStateException("Template TRAVEL_DESC_V1 missing."));

        List<Spot> spots = spotRepo.findAll();
        List<Language> languages = languageRepo.findAll();

        for (Spot spot : spots) {
            City city = spot.getCity();
            Country country = city.getCountry();

            for (Language language : languages) {

                // Skip duplicates
                if (responseRepo
                        .findBySpotAndLanguageAndTemplateKey(spot, language, "TRAVEL_DESC_V1")
                        .isPresent()) {
                    continue;
                }

                // Build final prompt
                String prompt = template.getText()
                        .replace("{spot}", spot.getName())
                        .replace("{city}", city.getName())
                        .replace("{country}", country.getName())
                        + "\n\nWrite the response in: " + language.getName();

                // Build messages for your chatOnce()
                List<Map<String, String>> messages = List.of(
                        Map.of("role", "system", "content", "You are an expert travel writer."),
                        Map.of("role", "user", "content", prompt)
                );

                // Call LLM (block for result)
                LLMResult result = openAIClient
                        .chatOnce("system", messages)
                        .onErrorReturn(new LLMResult("FAILED_TO_GENERATE", 0, 0))
                        .block();

                String content = result.getContent();
                int promptTokens = result.getPromptTokens();
                int completionTokens = result.getCompletionTokens();


                // Save to DB
                Response r = new Response();
                r.setSpot(spot);
                r.setLanguage(language);
                r.setTemplateKey("TRAVEL_DESC_V1");
                r.setContent(content);
                r.setModel("gpt-4o-mini");

                r.setTokensIn(promptTokens);
                r.setTokensOut(completionTokens);

                r.setCreatedAt(Instant.now());

                responseRepo.save(r);


                System.out.println("Saved → " +
                        spot.getName() + " | " + language.getName());
            }
        }
    }
}
