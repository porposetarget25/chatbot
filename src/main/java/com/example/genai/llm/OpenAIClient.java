package com.example.genai.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@Profile("openai")
public class OpenAIClient {

    private final WebClient http;

    @Value("${app.openai.api-base}")
    private String apiBase;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.chat-model}")
    private String chatModel;

    @Value("${app.openai.embed-model}")
    private String embedModel;

    // ✅ Inject the WebClient bean defined in your HttpConfig
    public OpenAIClient(WebClient http) {
        this.http = http;
        System.out.println("Using WebClient bean: " + http);
    }

    public Mono<String> chatOnce(String system, List<Map<String, String>> messages) {
        var payload = Map.of(
                "model", chatModel,
                "messages", messages,
                "temperature", 0.2
        );
        return http.post()
                .uri(apiBase + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> ((Map)((List) res.get("choices")).get(0)).get("message"))
                .map(msg -> (String) ((Map) msg).get("content"));
    }

    public Flux<String> chatStream(String system, List<Map<String, String>> messages) {
        var payload = Map.of(
                "model", chatModel,
                "messages", messages,
                "temperature", 0.2,
                "stream", true
        );
        return http.post()
                .uri(apiBase + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> Flux.fromArray(line.split("\n")))
                .filter(l -> l.startsWith("data:"))
                .map(l -> l.substring(5).trim())
                .filter(json -> !"[DONE]".equals(json))
                .map(json -> {
                    // naive chunk parse; good enough for demo
                    int idx = json.indexOf("\"content\"");
                    if (idx < 0) return "";
                    String tail = json.substring(idx + 9);
                    int q1 = tail.indexOf('"');
                    if (q1 < 0) return "";
                    int q2 = tail.indexOf('"', q1 + 1);
                    if (q2 < 0) return "";
                    return tail.substring(q1 + 1, q2);
                })
                .filter(s -> !s.isEmpty());
    }

    public Mono<float[]> embed(String text) {
        var payload = Map.of(
                "model", embedModel,
                "input", text
        );
        return http.post()
                .uri(apiBase + "/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> (Map) ((List) res.get("data")).get(0))
                .map(d -> (List<Double>) d.get("embedding"))
                .map(list -> {
                    float[] arr = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
                    return arr;
                });
    }
}
