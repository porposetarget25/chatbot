package com.example.genai.seed;

import com.example.genai.entity.PromptTemplate;
import com.example.genai.repo.PromptTemplateRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateSeeder implements CommandLineRunner {

    public static final String TEMPLATE_KEY_TRAVEL_DESC = "TRAVEL_DESC_V1";

    private final PromptTemplateRepo repo;

    public PromptTemplateSeeder(PromptTemplateRepo repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.findByKey(TEMPLATE_KEY_TRAVEL_DESC).isPresent()) {
            return;
        }

        String text = """
                Provide me an engaging, informative, and traveller-focused 300-word description of the {spot} in {city}, {country} in {language} language which includes accurate facts, figures, and local insights to make it both educational and inspiring. Cover the following aspects in a balanced and appealing manner: Historical Background: Outline the origins, key milestones, and evolution of the {spot} — including when and why it was built, who designed it, and its significance in {country}'s history and skyline. Cultural and Social Significance: Describe its role in {city}'s identity, connections with local traditions, community events, and how it contributes to {country}'s culture and tourism. Architectural Highlights: Explain its design features, construction style, materials used, and any engineering achievements or recognitions that make it unique. Interesting Facts and Hidden Gems: Share surprising details, lesser-known attractions within or around the {spot}, and fun trivia that sparks curiosity for travellers. Traveller Experience and Recommendations: Describe what visitors can enjoy — from viewpoints and guided tours to nearby attractions — and offer practical insights for making the most of their visit. Ensure the tone is storytelling, fact-rich, and traveller-friendly, with smooth transitions and natural flow. Avoid generic descriptions and focus on vivid details that help readers visualize the experience and feel inspired to visit. Do not format in bullet points; write it as a continuous essay. The content must be original and copyright-free, written in fluent and descriptive language.
                """;


        PromptTemplate tmpl = new PromptTemplate(null, TEMPLATE_KEY_TRAVEL_DESC, text);
        repo.save(tmpl);
    }
}
