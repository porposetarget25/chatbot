package com.example.genai.service;

import com.example.genai.entity.Response;
import com.example.genai.repo.ResponseRepo;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.StandardCopyOption;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAudioService {

    private final ResponseRepo responseRepo;
    private final AudioMixService audioMixService;
    private final WebClient geminiClient;

    private static final int SAMPLE_RATE = 24000;
    private static final short CHANNELS = 1;
    private static final short BITS = 16;
    private Path bgAudioPath;

    @Value("${gemini.tts.voiceName:Leda}")
    private String voiceName;

    private final Path outputDir =
            Paths.get("C:\\Users\\mohahama\\Downloads\\World-Traveler\\audio-mix-gemini");

    //private final Path bgAudioPath =
            //Paths.get("java-genai-llm-backend/src/main/resources/bg-audio/Calm1.mp3");

    public GeminiAudioService(ResponseRepo responseRepo,
                              AudioMixService audioMixService,
                              @Qualifier("geminiWebClient") WebClient geminiClient) {
        this.responseRepo = responseRepo;
        this.audioMixService = audioMixService;
        this.geminiClient = geminiClient;
    }
    @PostConstruct
    public void initBgAudio() throws Exception {
        // Loads from: src/main/resources/bg-audio/Calm1.mp3
        this.bgAudioPath = extractClasspathResourceToTemp("bg-audio/Calm1.mp3");
        System.out.println("Background audio resolved to → " + bgAudioPath.toAbsolutePath());
    }

    private Path extractClasspathResourceToTemp(String classpathLocation) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Classpath resource not found: " + classpathLocation);
        }

        String safeName = classpathLocation.replace("/", "_");
        Path tempFile = java.nio.file.Files.createTempFile("bg_", "_" + safeName);

        try (InputStream in = resource.getInputStream()) {
            java.nio.file.Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    @Transactional
    public void generateAllGeminiAudio() throws Exception {

        Files.createDirectories(outputDir);

        for (Response r : responseRepo.findAll()) {

            String baseName = r.getSpot().getName().replace(" ", "_")
                    + "_" + r.getLanguage().getName().replace(" ", "_");

            Path raw = outputDir.resolve(baseName + "_raw.wav");
            Path mixed = outputDir.resolve(baseName + "_mixed.wav");

            if (Files.exists(mixed)) {
                System.out.println("Already exists → " + mixed);
                continue;
            }

            System.out.println("Calling Gemini 2.5 Flash Preview TTS for → " + baseName);

            byte[] pcm = callGeminiTts(r.getContent());

            byte[] wav = pcmToWav(pcm, SAMPLE_RATE, CHANNELS, BITS);
            Files.write(raw, wav);

            audioMixService.mixSpeechWithBackground(raw, bgAudioPath, mixed);
        }
    }

    /* ================= GEMINI CALL ================= */

    private byte[] callGeminiTts(String text) {

        return geminiClient.post()
                .uri("/v1beta/models/gemini-2.5-flash-preview-tts:generateContent")
                .bodyValue(buildGeminiTtsRequest(text))
                .exchangeToMono(this::handleResponse)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .map(this::extractPcm)
                .block();
    }

    private Mono<JsonNode> handleResponse(ClientResponse response) {

        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(JsonNode.class);
        }

        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(
                        new RuntimeException(
                                "Gemini API error " +
                                        response.statusCode().value() +
                                        " : " + body
                        )
                ));
    }

    private byte[] extractPcm(JsonNode json) {

        JsonNode node = json.at("/candidates/0/content/parts/0/inlineData/data");

        if (node.isMissingNode()) {
            throw new IllegalStateException("No audio returned by Gemini: " + json);
        }

        return Base64.getDecoder().decode(node.asText());
    }

    /* ================= REQUEST ================= */

    private Map<String, Object> buildGeminiTtsRequest(String text) {

        return Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text",
                                        "Please narrate in a warm, friendly travel-guide tone:\n\n" + text)
                        ))
                ),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of(
                                "voiceConfig", Map.of(
                                        "prebuiltVoiceConfig", Map.of(
                                                "voiceName", voiceName
                                        )
                                )
                        )
                )
        );
    }

    /* ================= PCM → WAV ================= */

    private static byte[] pcmToWav(byte[] pcm, int rate, short channels, short bits)
            throws IOException {

        int byteRate = rate * channels * bits / 8;
        int dataSize = pcm.length;
        int chunkSize = 36 + dataSize;

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write("RIFF".getBytes());
        out.write(intLE(chunkSize));
        out.write("WAVE".getBytes());
        out.write("fmt ".getBytes());
        out.write(intLE(16));
        out.write(shortLE((short) 1));
        out.write(shortLE(channels));
        out.write(intLE(rate));
        out.write(intLE(byteRate));
        out.write(shortLE((short) (channels * bits / 8)));
        out.write(shortLE(bits));
        out.write("data".getBytes());
        out.write(intLE(dataSize));
        out.write(pcm);

        return out.toByteArray();
    }

    private static byte[] intLE(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] shortLE(short v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array();
    }
}
