package com.example.genai.service;

import com.example.genai.entity.*;
import com.example.genai.llm.OpenAIClient;
import com.example.genai.repo.*;
import com.example.genai.seed.PromptTemplateSeeder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TravelDescriptionBatchService {

    private final SpotRepo spotRepo;
    private final LanguageRepo languageRepo;
    private final PromptTemplateRepo templateRepo;
    private final ResponseRepo responseRepo;
    private final OpenAIClient openai;

    public TravelDescriptionBatchService(
            SpotRepo spotRepo,
            LanguageRepo languageRepo,
            PromptTemplateRepo templateRepo,
            ResponseRepo responseRepo,
            OpenAIClient openai
    ) {
        this.spotRepo = spotRepo;
        this.languageRepo = languageRepo;
        this.templateRepo = templateRepo;
        this.responseRepo = responseRepo;
        this.openai = openai;
    }

    @Transactional
    public void generateAll() {
        PromptTemplate template = templateRepo
                .findByKey(PromptTemplateSeeder.TEMPLATE_KEY_TRAVEL_DESC)
                .orElseThrow(() -> new IllegalStateException("Template not found"));

        List<Spot> spots = spotRepo.findAll();
        List<Language> languages = languageRepo.findAll();

        for (Spot spot : spots) {
            City city = spot.getCity();
            Country country = city.getCountry();

            for (Language lang : languages) {
                // Skip if already generated
                if (responseRepo
                        .findBySpotIdAndLanguageIdAndTemplateKey(
                                spot.getId(), lang.getId(), template.getKey()
                        ).isPresent()) {
                    continue;
                }

                String prompt = render(template.getText(),
                        spot.getName(), city.getName(), country.getName());

                String system = "You are an expert travel writer. " +
                        "Write the answer in " + lang.getName() + " only.";

                var messages = List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", prompt)
                );

                // chatOnce returns Mono<String>; we block here since this is a batch job
                String answer = openai.chatOnce(system, messages)
                        .map(t -> t.replaceAll("\\s*\\R+\\s*", " ").trim())
                        .onErrorReturn("ERROR generating description")
                        .block();

                Response resp = new Response();
                resp.setSpot(spot);
                resp.setLanguage(lang);
                resp.setTemplateKey(template.getKey());
                resp.setContent(answer);
                resp.setModel("openai:" + /* your model name */ "gpt-4o-mini");
                resp.setCreatedAt(Instant.now());

                responseRepo.save(resp);
            }
        }
    }

    private String render(String template, String spot, String city, String country) {
        return template
                .replace("{spot}", spot)
                .replace("{city}", city)
                .replace("{country}", country)
                .trim();
    }
}
