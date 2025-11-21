package com.example.genai.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;

@Component
@Profile("openai")
public class OpenAIClient {

    private final WebClient http;      // for chat / embeddings (JSON)
    private final WebClient audioHttp; // for TTS (large audio)

    private final String apiBase;
    private final String apiKey;
    private final String chatModel;
    private final String embedModel;

    public OpenAIClient(
            @Value("${app.openai.api-base}") String apiBase,
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.chat-model}") String chatModel,
            @Value("${app.openai.embed-model}") String embedModel
    ) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embedModel = embedModel;

        // DEV ONLY: trust-all SSL to bypass PKIX issues (do NOT use in production)
        SslContext sslContext;
        try {
            sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build SSL context for WebClient", e);
        }

        HttpClient nettyClient = HttpClient.create()
                .secure(ssl -> ssl.sslContext(sslContext));

        ReactorClientHttpConnector insecureConnector =
                new ReactorClientHttpConnector(nettyClient);

        // Normal JSON WebClient (chat, embeddings)
        this.http = WebClient.builder()
                .baseUrl(apiBase)
                .clientConnector(insecureConnector)
                .build();

        // Audio WebClient with larger buffer for MP3
        ExchangeStrategies bigBuffer = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16 MB
                .build();

        this.audioHttp = WebClient.builder()
                .baseUrl(apiBase)
                .exchangeStrategies(bigBuffer)
                .clientConnector(insecureConnector)
                .build();
    }

    // ---------- CHAT ONCE (returns text + token usage) ----------

    public Mono<LLMResult> chatOnce(String system, List<Map<String, String>> messages) {

        var msgList = new java.util.ArrayList<Map<String, String>>(
                messages == null ? java.util.List.of() : messages
        );
        if (system != null && !system.isBlank()) {
            msgList.add(0, Map.of("role", "system", "content", system));
        }

        Map<String, Object> payload = Map.of(
                "model", chatModel,
                "messages", msgList,
                "temperature", 0.2
        );

        return http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(res -> {
                    // ---- token usage ----
                    int promptTokens = 0;
                    int completionTokens = 0;

                    Object usageObj = res.get("usage");
                    if (usageObj instanceof Map<?, ?> usage) {
                        promptTokens = safeInt(usage.get("prompt_tokens"));
                        completionTokens = safeInt(usage.get("completion_tokens"));
                    }

                    // ---- content ----
                    Object choicesObj = res.get("choices");
                    if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                        return new LLMResult("", promptTokens, completionTokens);
                    }

                    Object first = choices.get(0);
                    if (!(first instanceof Map<?, ?> firstMap)) {
                        return new LLMResult("", promptTokens, completionTokens);
                    }

                    Object messageObj = firstMap.get("message");
                    String content = "";

                    if (messageObj instanceof Map<?, ?> msgMap) {
                        Object c = msgMap.get("content");
                        content = c == null ? "" : c.toString();
                    }

                    // clean newlines
                    content = content.replaceAll("\\s*\\n+\\s*", " ").trim();

                    return new LLMResult(content, promptTokens, completionTokens);
                });
    }

    // ---------- TEXT → SPEECH (MP3) ----------

    public Mono<TtsResult> textToSpeech(String text, String voice) {
        String instructions = "Please respond in sweet and friendly way so that the listener would be attracted and listens !!";
        Map<String, Object> payload = Map.of(
                "model", "gpt-4o-mini-tts",
                "input", text,
                "voice", voice,
                "format", "mp3",
                "instructions", instructions
        );

        return audioHttp.post()
                .uri("/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    int promptTokens = 0;      // TTS endpoint currently doesn't return usage
                    int completionTokens = 0;

                    System.out.println("TTS call completed, bytes length = " +
                            (bytes != null ? bytes.length : 0));
                    System.out.println("TTS Tokens → IN=" + promptTokens +
                            " OUT=" + completionTokens);

                    return new TtsResult(bytes, promptTokens, completionTokens);
                });
    }

    // ---------- helpers ----------

    private int safeInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
