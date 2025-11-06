package com.example.genai.controller;

import com.example.genai.llm.OpenAIClient;
import com.example.genai.llm.OllamaClient;
import com.example.genai.rag.EmbeddingStore;
import com.example.genai.rag.TextChunk;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private final ApplicationContext ctx;
    private final EmbeddingStore store = new EmbeddingStore();

    @Autowired
    public ChatController(ApplicationContext ctx) { this.ctx = ctx; }

    @PostMapping("/chat")
    public Mono<Map<String, Object>> chat(@RequestBody ChatRequest req) {
        String system = Optional.ofNullable(req.system()).orElse("You are a helpful Java assistant.");
        String user = req.prompt();

        if (ctx.containsBean("openAIClient")) {
            var client = ctx.getBean(OpenAIClient.class);
            List<Map<String,String>> msgs = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)
            );
            return client.chatOnce(system, msgs)
                    .map(content -> Map.of("answer", content));
        } else {
            var client = ctx.getBean(OllamaClient.class);
            return client.chatOnce(system, user)
                    .map(content -> Map.of("answer", content));
        }
    }

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest req) {
        String system = Optional.ofNullable(req.system()).orElse("You are a helpful Java assistant.");
        String user = req.prompt();

        if (ctx.containsBean("openAIClient")) {
            var client = ctx.getBean(OpenAIClient.class);
            List<Map<String,String>> msgs = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)
            );
            return client.chatStream(system, msgs);
        } else {
            var client = ctx.getBean(OllamaClient.class);
            return client.chatStream(system, user);
        }
    }

    @PostMapping("/rag/upsert")
    public Mono<Map<String, Object>> upsert(@RequestBody RagUpsert req) {
        if (ctx.containsBean("openAIClient")) {
            var client = ctx.getBean(OpenAIClient.class);
            return client.embed(req.text()).map(vec -> {
                store.upsert(new TextChunk(req.id(), req.text(), vec));
                return Map.of("ok", true);
            });
        }
        return Mono.just(Map.of("ok", false, "reason", "Embeddings not wired for this profile"));
    }

    @PostMapping("/rag/query")
    public Mono<Map<String, Object>> ragQuery(@RequestBody RagQuery req) {
        String prompt = req.prompt();
        int k = Optional.ofNullable(req.k()).orElse(3);
        if (ctx.containsBean("openAIClient")) {
            var client = ctx.getBean(OpenAIClient.class);
            return client.embed(prompt).flatMap(vec -> {
                var top = store.topK(vec, k);
                String context = top.stream().map(TextChunk::text).reduce("", (a,b) -> a + "\n---\n" + b);
                var messages = List.of(
                        Map.of("role","system","content","Use the provided context to answer. If unsure, say you don't know."),
                        Map.of("role","user","content","Context:\n" + context + "\n\nQuestion: " + prompt)
                );
                return client.chatOnce(null, messages).map(answer -> Map.of(
                        "answer", answer,
                        "sources", top.stream().map(TextChunk::id).toList()
                ));
            });
        }
        return Mono.just(Map.of("answer","Embeddings not wired for this profile"));
    }

    public record ChatRequest(@NotBlank String prompt, String system) {}
    public record RagUpsert(@NotBlank String id, @NotBlank String text) {}
    public record RagQuery(@NotBlank String prompt, Integer k) {}
}
