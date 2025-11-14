package com.example.genai.service;

import com.example.genai.entity.PromptTemplate;
import com.example.genai.entity.Response;
import com.example.genai.entity.Language;

import com.example.genai.repo.PromptTemplateRepo;
import com.example.genai.repo.ResponseRepo;
import com.example.genai.repo.LanguageRepo;

import com.example.genai.llm.OpenAIClient;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
public class BatchRunner {

    private final PromptTemplateRepo templRepo;
    private final ResponseRepo respRepo;
    private final LanguageRepo langRepo;
    private final OpenAIClient openai; // your chatOnce client

    public BatchRunner(PromptTemplateRepo t, ResponseRepo r, LanguageRepo l, OpenAIClient o) {
        this.templRepo = t; this.respRepo = r; this.langRepo = l; this.openai = o;
    }

    @Transactional
    public Mono<Void> runForAllLanguages(Path excelFile) throws IOException {
        // 1) Import or re-import prompts from Excel
        try (InputStream in = Files.newInputStream(excelFile)) {
            // inject ExcelImporter bean and call importData(in);
        }

        // 2) For each template, generate for each language (sequential to respect rate limits)
        List<PromptTemplate> templates = templRepo.findAll();
        List<Language> langs = langRepo.findAll();

        return Flux.fromIterable(templates)
                .concatMap(tpl -> Flux.fromIterable(langs)
                        .concatMap(lang -> generateIfMissing(tpl, lang))
                )
                .then();
    }

    private Mono<Response> generateIfMissing(PromptTemplate tpl, Language lang) {
        return Mono.defer(() -> {
            // Skip if already exists
            var existing = respRepo.findBySpotIdAndLanguageIdAndPromptKey(
                    tpl.getSpot().getId(), lang.getId(), tpl.getPromptKey());
            if (existing.isPresent()) return Mono.just(existing.get());

            // Build messages (system→ respond in X language)
            var system = "Respond strictly in " + lang.getName() +
                    ". Convert newlines to spaces; be concise and factual.";
            var messages = List.of(
                    Map.of("role","system","content", system),
                    Map.of("role","user","content", tpl.getText())
            );

            return openai.chatOnce(system, messages)
                    .map(content -> {
                        Response r = new Response();
                        r.setSpot(tpl.getSpot());
                        r.setLanguage(lang);
                        r.setPromptKey(tpl.getPromptKey());
                        r.setContent(content);
                        r.setModel("openai:" + /* inject model name if you expose it */ "gpt-4o-mini");
                        r.setTokensIn(null);
                        r.setTokensOut(null);
                        r.setCreatedAt(Instant.now());
                        return respRepo.save(r);
                    });
        });
    }
}

