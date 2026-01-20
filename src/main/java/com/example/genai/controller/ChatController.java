package com.example.genai.controller;

import com.example.genai.component.ChatMemoryStore;
import com.example.genai.llm.LLMResult;
import com.example.genai.llm.OpenAIClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api")
@Validated
public class ChatController {

    private final OpenAIClient client;
    private final ChatMemoryStore memoryStore;

    @Autowired
    public ChatController(OpenAIClient client, ChatMemoryStore memoryStore) {
        this.client = client;
        this.memoryStore = memoryStore;
    }

    /**
     * SINGLE CHAT (unchanged behaviour: request -> answer)
     * Keeps your existing endpoint working.
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        String system = Optional.ofNullable(req.system())
                .orElse("You are a helpful Java assistant.");
        String user = req.prompt();

        // Pass only the user message; chatOnce() will inject the system message.
        var msgs = List.of(
                Map.of("role", "user", "content", user)
        );

        LLMResult answer = client.chatOnce(system, msgs).block();

        return Map.of("answer", answer != null ? answer.getContent() : "");
    }

    /**
     * STREAMING INTERACTIVE CHAT (SSE)
     * - Loads conversation history by sessionId
     * - Appends the new user message immediately
     * - Streams assistant tokens back as SSE
     * - Saves final assistant response in memory store at completion
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest req) {

        String sessionId = Optional.ofNullable(req.sessionId()).orElse("default");
        String system = Optional.ofNullable(req.system()).orElse("You are a helpful Java assistant.");
        String user = req.prompt();

        // 1) Build messages: system + history + user
        List<Map<String, String>> history = memoryStore.get(sessionId);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", user));

        // 2) Persist user message immediately
        memoryStore.append(sessionId, Map.of("role", "user", "content", user));

        // 3) Buffer assistant response so we can store it at the end
        StringBuilder assistantBuffer = new StringBuilder();

        // CHANGE: buffer tokens into larger chunks for smoother UI
        Flux<ServerSentEvent<String>> tokenEvents =
                client.chatStream(system, messages)
                        .bufferTimeout(50, Duration.ofMillis(200))     // up to 50 tokens or 200ms
                        .map(list -> String.join("", list))            // join tokens into one chunk
                        .filter(chunk -> chunk != null && !chunk.isBlank())
                        .doOnNext(chunk -> assistantBuffer.append(chunk))
                        .map(chunk -> ServerSentEvent.builder(chunk).event("token").build())
                        .doOnError(e -> {
                            System.out.println("chatStream error: " + e.getMessage());
                        })
                        .doFinally(sig -> {
                            String assistant = assistantBuffer.toString().trim();
                            if (!assistant.isBlank()) {
                                memoryStore.append(sessionId, Map.of("role", "assistant", "content", assistant));
                            }
                        });

        // Optional keep-alive (helps with proxies/timeouts)
        Flux<ServerSentEvent<String>> keepAlive =
                Flux.interval(Duration.ofSeconds(15))
                        .map(i -> ServerSentEvent.<String>builder("")
                                .comment("keep-alive")
                                .build())
                        // 🔑 stop keep-alive when token stream completes
                        .takeUntilOther(tokenEvents.ignoreElements());

        // Optional first event so clients immediately see something
        Flux<ServerSentEvent<String>> start =
                Flux.just(ServerSentEvent.builder("connected").event("status").build());

        return Flux.concat(
                start,
                Flux.merge(tokenEvents, keepAlive),
                Flux.defer(() -> Flux.just(
                        ServerSentEvent.builder(assistantBuffer.toString().trim())
                                .event("message")
                                .build()
                )),
                Flux.just(ServerSentEvent.builder("[DONE]").event("done").build())
        );
    }


    @DeleteMapping("/chat/{sessionId}")
    public Map<String, Object> reset(@PathVariable("sessionId") String sessionId) {
        memoryStore.clear(sessionId);
        return Map.of("status", "cleared", "sessionId", sessionId);
    }


    public record ChatRequest(String sessionId, String system, String prompt) {}
}
