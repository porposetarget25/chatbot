package com.example.genai.component;

import com.example.genai.entity.Language;
import com.example.genai.repo.LanguageRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LanguageSeeder implements CommandLineRunner {
    private final LanguageRepo repo;
    public LanguageSeeder(LanguageRepo repo){ this.repo = repo; }

    @Override public void run(String... args) {
        List<String> names = List.of(
                "English","Hindi","Spanish","Mandarin Chinese","French","German","Japanese",
                "Portuguese","Russian","Italian","Arabic","Korean","Turkish","Dutch","Thai",
                "Indonesian (Bahasa Indonesia)","Malay (Bahasa Melayu)","Vietnamese","Greek","Swedish"
        );
        Map<String,String> code = Map.ofEntries(
                Map.entry("English","en"), Map.entry("Hindi","hi"), Map.entry("Spanish","es"),
                Map.entry("Mandarin Chinese","zh"), Map.entry("French","fr"), Map.entry("German","de"),
                Map.entry("Japanese","ja"), Map.entry("Portuguese","pt"), Map.entry("Russian","ru"),
                Map.entry("Italian","it"), Map.entry("Arabic","ar"), Map.entry("Korean","ko"),
                Map.entry("Turkish","tr"), Map.entry("Dutch","nl"), Map.entry("Thai","th"),
                Map.entry("Indonesian (Bahasa Indonesia)","id"), Map.entry("Malay (Bahasa Melayu)","ms"),
                Map.entry("Vietnamese","vi"), Map.entry("Greek","el"), Map.entry("Swedish","sv")
        );
        for (String n: names) {
            repo.findByNameIgnoreCase(n).orElseGet(() ->
                    repo.save(new Language(null, code.getOrDefault(n, n.substring(0,2).toLowerCase()), n))
            );
        }
    }
}

