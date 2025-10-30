package com.example.genai.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Profile("ollama")
public class OllamaClient {
    private final WebClient http = null;
    @Value("${app.ollama.api-base}") String apiBase;
    @Value("${app.ollama.chat-model}") String chatModel;

    public OllamaClient(String apiBase, String chatModel) {
        this.apiBase = apiBase;
        this.chatModel = chatModel;
    }

    public Mono<String> chatOnce(String system, String userPrompt) {
        var payload = Map.of(
                "model", chatModel,
                "prompt", (system == null ? "" : ("System: " + system + "\n")) + userPrompt,
                "stream", false
        );
        return http.post()
                .uri(apiBase + "/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> (String) res.getOrDefault("response", ""));
    }

    public Flux<String> chatStream(String system, String userPrompt) {
        var payload = Map.of(
                "model", chatModel,
                "prompt", (system == null ? "" : ("System: " + system + "\n")) + userPrompt,
                "stream", true
        );
        return http.post()
                .uri(apiBase + "/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(chunk -> (String) chunk.getOrDefault("response", ""));
    }
}
