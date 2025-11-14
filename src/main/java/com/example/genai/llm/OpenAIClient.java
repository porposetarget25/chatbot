package com.example.genai.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClientResponseException;


import java.util.List;
import java.util.Map;
import reactor.util.retry.Retry;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Duration;

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
                .map(msg -> {
                    String content = (String) ((Map) msg).get("content");
                    return content == null ? "" : content;
                }).map(content -> content.replaceAll("\\s*\\R+\\s*", " ").trim());
    }

    /*public Mono<String> chatOnce(String system, List<Map<String, String>> messages) {
        var msgList = new java.util.ArrayList<Map<String, String>>(
                messages == null ? java.util.List.of() : messages
        );
        if (system != null && !system.isBlank()) {
            msgList.add(0, java.util.Map.of("role", "system", "content", system));
        }

        var payload = java.util.Map.<String, Object>of(
                "model", chatModel,
                "messages", msgList,
                "temperature", 0.2
        );

        return http.post()
                .uri(apiBase + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new WebClientResponseException(
                                        "OpenAI API error: HTTP " + response.statusCode().value()
                                                + (body.isBlank() ? "" : " - " + body),
                                        response.statusCode().value(), // Spring 6+ safe
                                        null, // no reason phrase on HttpStatusCode
                                        null, null, null
                                )))
                )
                .bodyToMono(new ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                //  Log token usage when present, but keep the return type Mono<String>
                .doOnNext(res -> {
                    Object usageObj = res.get("usage");
                    if (usageObj instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> u = (Map<String, Object>) usageObj;

                        int prompt     = asInt(u.get("prompt_tokens"));
                        int completion = asInt(u.get("completion_tokens"));
                        int total      = asInt(u.get("total_tokens"));

                        Object model = res.get("model");
                        System.out.println("OpenAI usage — input tokens=" + prompt + ", response tokens=" + completion + ", total tokens=" + total);

                    }
                })
                .map(res -> {
                    // If API returns: { "error": { "message": "..." } }
                    Object err = res.get("error");
                    if (err instanceof Map<?, ?> em) {
                        Object emsg = em.get("message");
                        if (emsg != null) {
                            throw new RuntimeException("OpenAI error: " + emsg);
                        }
                    }

                    Object choicesObj = res.get("choices");
                    if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                        return "";
                    }

                    Object first = choices.get(0);
                    if (!(first instanceof Map<?, ?> firstMap)) {
                        return "";
                    }

                    Object messageObj = firstMap.get("message");
                    if (messageObj instanceof Map<?, ?> msgMap) {
                        Object content = msgMap.get("content");
                        // 🔹 Clean formatting: remove double newlines and trim
                        return content == null
                                ? ""
                                : content.toString()
                                .replaceAll("\\s*\\n+\\s*", " ") // convert newlines to spaces
                                .trim();
                    }

                    // Fallback for providers returning 'text'
                    Object text = firstMap.get("text");
                    return text == null ? "" : text.toString()
                            .replaceAll("\\s*\\n+\\s*", " ")
                            .trim();
                })

                .defaultIfEmpty("");
    }*/

    private static int asInt(Object v) {
        return (v instanceof Number n) ? n.intValue() : 0;
    }


    public Flux<String> chatStream(String system, List<Map<String, String>> messages) {
        var payload = Map.of("model", chatModel, "messages", messages, "temperature", 0.2, "stream", true);

        return http.post()
                .uri(apiBase + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToFlux(String.class)
                .retryWhen(rateLimitRetrySpec())        // <— add retry here
                .flatMap(line -> Flux.fromArray(line.split("\n")))
                .filter(l -> l.startsWith("data:"))
                .map(l -> l.substring(5).trim())
                .filter(json -> !"[DONE]".equals(json))
                .map(json -> {
                    int idx = json.indexOf("\"content\"");
                    if (idx < 0) return "";
                    String tail = json.substring(idx + 9);
                    int q1 = tail.indexOf('"'); if (q1 < 0) return "";
                    int q2 = tail.indexOf('"', q1 + 1); if (q2 < 0) return "";
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

    private Retry rateLimitRetrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))  // up to 3 retries, 2s, 4s, 8s
                .filter(ex -> {
                    if (ex instanceof WebClientResponseException w) {
                        int s = w.getStatusCode().value();
                        return s == 429 || (s >= 500 && s != 501 && s != 505);
                    }
                    return false;
                })
                .jitter(0.5) // +/- 50% jitter
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }
}
