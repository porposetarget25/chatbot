package com.example.genai.llm;

import com.example.genai.util.StreamJsonParsers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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

        this.http = WebClient.builder()
                .baseUrl(apiBase)
                .clientConnector(insecureConnector)
                .build();

        // If you still need the larger buffer for audio, keep your existing audio client setup.
        this.audioHttp = WebClient.builder()
                .baseUrl(apiBase)
                .clientConnector(insecureConnector)
                .build();
    }

    // ---------- CHAT ONCE (returns text + token usage) ----------

    public Mono<LLMResult> chatOnce(String system, List<Map<String, String>> messages) {

        var msgList = new ArrayList<Map<String, String>>(messages == null ? List.of() : messages);

        // Ensure system is at the beginning only once
        if (system != null && !system.isBlank()) {
            if (msgList.isEmpty() || !"system".equals(msgList.get(0).get("role"))) {
                msgList.add(0, Map.of("role", "system", "content", system));
            }
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
                    int promptTokens = 0;
                    int completionTokens = 0;

                    Object usageObj = res.get("usage");
                    if (usageObj instanceof Map<?, ?> usage) {
                        promptTokens = safeInt(usage.get("prompt_tokens"));
                        completionTokens = safeInt(usage.get("completion_tokens"));
                    }

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

                    content = content.replaceAll("\\s*\\n+\\s*", " ").trim();
                    return new LLMResult(content, promptTokens, completionTokens);
                });
    }

    // ---------- CHAT STREAM (SSE tokens) ----------

    /**
     * Streams assistant tokens as Flux<String>.
     * Robust SSE parsing using DataBuffer + "\n\n" event boundary.
     */
    public Flux<String> chatStream(String system, List<Map<String, String>> messages) {

        return Flux.defer(() -> {
            var msgList = new ArrayList<Map<String, String>>(messages == null ? List.of() : messages);

            // Ensure system is at the beginning only once
            if (system != null && !system.isBlank()) {
                if (msgList.isEmpty() || !"system".equals(msgList.get(0).get("role"))) {
                    msgList.add(0, Map.of("role", "system", "content", system));
                }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", chatModel);
            payload.put("messages", msgList);
            payload.put("temperature", 0.2);
            payload.put("stream", true);

            // IMPORTANT: buffer must be per-subscription and processed sequentially
            final StringBuilder buf = new StringBuilder();

            return http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .headers(h -> h.setBearerAuth(apiKey))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)

                    // CRITICAL: concatMap ensures sequential processing (no concurrent delete/append)
                    .concatMap(db -> {
                        try {
                            byte[] bytes = new byte[db.readableByteCount()];
                            db.read(bytes);
                            String chunk = new String(bytes, StandardCharsets.UTF_8);

                            buf.append(chunk);

                            List<String> out = new ArrayList<>();

                            while (true) {
                                int idxLfLf = buf.indexOf("\n\n");
                                int idxCrLfCrLf = buf.indexOf("\r\n\r\n");

                                int idx;
                                int delimLen;

                                if (idxLfLf >= 0 && (idxCrLfCrLf < 0 || idxLfLf < idxCrLfCrLf)) {
                                    idx = idxLfLf;
                                    delimLen = 2;
                                } else if (idxCrLfCrLf >= 0) {
                                    idx = idxCrLfCrLf;
                                    delimLen = 4;
                                } else {
                                    break; // no complete SSE event yet
                                }

                                String eventBlock = buf.substring(0, idx);
                                buf.delete(0, idx + delimLen);

                                // Parse SSE lines
                                for (String line : eventBlock.split("\\r?\\n")) {
                                    line = line.trim();
                                    if (!line.startsWith("data:")) continue;

                                    String data = line.substring("data:".length()).trim();
                                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                                    // Your parser: should extract delta content tokens
                                    List<String> tokens = StreamJsonParsers.extractDeltaContent(data);
                                    if (tokens != null && !tokens.isEmpty()) {
                                        out.addAll(tokens);
                                    }
                                }
                            }

                            return Flux.fromIterable(out);

                        } finally {
                            DataBufferUtils.release(db);
                        }
                    })
                    .filter(s -> s != null && !s.isBlank());
        });
    }


    // ---------- helpers ----------

    private int safeInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    // ---------- TEXT → SPEECH (MP3) ----------

    public Mono<TtsResult> textToSpeech(String text, String voice) {
        String instructions = "Please respond in sweet and friendly way so that the listener would be attracted and listens !!";
        Map<String, Object> payload = Map.of(
                "model", "gpt-4o-mini-tts",
                "input", text,
                "voice", voice,
                "format", "wav",
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
}
