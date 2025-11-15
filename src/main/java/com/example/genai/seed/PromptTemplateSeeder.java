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
                Provide me an engaging, informative, and traveller-focused 300-word description of the {spot}, {city}, {country}.
                The content must be original and copyright-free, written in fluent and descriptive language.
                It should include accurate facts, figures, and local insights to make it both educational and inspiring. Cover the following aspects in a balanced and appealing manner:
                
                Historical Background: Outline the origins, key milestones, and evolution of the {spot} — including when and why it was built, who designed it, and its significance in {country}'s skyline.
                
                Cultural and Social Significance: Describe its role in {city}'s identity, connections with local traditions, community events, and how it contributes to {country}'s culture and tourism.
                
                Architectural Highlights: Explain its design features, height, construction materials, and any world records or engineering achievements that make it unique.
                
                Interesting Facts and Hidden Gems: Share surprising details, lesser-known attractions within or around the {spot}, and fun trivia that adds curiosity for travelers.
                
                Traveler Experience and Recommendations: Describe what visitors can enjoy — from observation decks and dining to adventure activities — and offer practical insights for making the most of their visit.
                
                Ensure the tone is storytelling, fact-rich, and traveler-friendly, with smooth transitions and natural flow. Avoid generic descriptions and focus on details that help readers visualize the experience and feel inspired to visit.
                """;

        PromptTemplate tmpl = new PromptTemplate(null, TEMPLATE_KEY_TRAVEL_DESC, text);
        repo.save(tmpl);
    }
}
