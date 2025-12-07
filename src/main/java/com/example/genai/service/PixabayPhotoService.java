package com.example.genai.service;

import com.example.genai.entity.City;
import com.example.genai.entity.Country;
import com.example.genai.entity.Spot;
import com.example.genai.repo.SpotRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

@Service
public class PixabayPhotoService {

    private final SpotRepo spotRepo;
    private final WebClient webClient;
    private final String pixabayApiKey;

    // Output directory for Pixabay images
    private final Path outputDir = Paths.get(
            "C:\\Users\\mohahama\\Downloads\\World-Traveler\\generated_photos_pixabay"
    );

    public PixabayPhotoService(
            SpotRepo spotRepo,
            WebClient.Builder webClientBuilder,
            @Value("${app.pixabay.api-key}") String pixabayApiKey
    ) {
        this.spotRepo = spotRepo;
        this.pixabayApiKey = pixabayApiKey;

        // ❗ DEV-ONLY: trust all certificates (bypass PKIX validation)
        HttpClient httpClient;
        try {
            httpClient = HttpClient.create()
                    .secure(ssl -> {
                        try {
                            ssl.sslContext(
                                    SslContextBuilder.forClient()
                                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                            .build()
                            );
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to build insecure SSL context", e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Error configuring insecure HttpClient for Pixabay", e);
        }

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        this.webClient = webClientBuilder
                .baseUrl("https://pixabay.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Downloads up to 20 photos for each spot in the DB using Pixabay.
     * @return number of spots processed
     */
    @Transactional(readOnly = true)
    public int downloadPhotosForAllSpotsFromPixabay() {
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create output directory: " + outputDir, e);
        }

        List<Spot> spots = spotRepo.findAll();
        for (Spot spot : spots) {
            try {
                downloadPhotosForSpot(spot);
            } catch (Exception ex) {
                System.err.println("Failed to download Pixabay photos for spot: "
                        + spot.getName() + " – " + ex.getMessage());
            }
        }
        return spots.size();
    }

    /**
     * For one spot, build a query "spot, city, country" and ask Pixabay for images.
     * Saves up to 20 images as {spotName}_1.jpg ... {spotName}_20.jpg
     */
    private void downloadPhotosForSpot(Spot spot) throws IOException {

        City city = spot.getCity();
        Country country = city.getCountry();

        String query = buildQuery(spot, city, country);
        System.out.println("Pixabay searching photos for: " + query);

        Map<String, Object> apiResponse = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/")
                        .queryParam("key", pixabayApiKey)
                        .queryParam("q", query)
                        .queryParam("image_type", "photo")
                        .queryParam("per_page", 50)          // ask for up to 50, we’ll use max 20
                        .queryParam("safesearch", "true")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (apiResponse == null) {
            System.out.println("No response from Pixabay for: " + query);
            return;
        }

        Object hitsObj = apiResponse.get("hits");
        if (!(hitsObj instanceof List<?> hits) || hits.isEmpty()) {
            System.out.println("No Pixabay hits for: " + query);
            return;
        }

        String baseName = normalizeFileName(spot.getName());

        int max = Math.min(20, hits.size());
        for (int i = 0; i < max; i++) {
            Object hitObj = hits.get(i);
            if (!(hitObj instanceof Map<?, ?> hit)) {
                continue;
            }

            // Prefer largeImageURL, fallback to webformatURL
            Object largeUrlObj = hit.get("largeImageURL");
            Object webUrlObj = hit.get("webformatURL");

            String imageUrl = null;
            if (largeUrlObj instanceof String s && !s.isBlank()) {
                imageUrl = s;
            } else if (webUrlObj instanceof String s2 && !s2.isBlank()) {
                imageUrl = s2;
            }

            if (imageUrl == null) {
                System.out.println("No usable image URL for hit " + (i + 1) + " / " + query);
                continue;
            }

            String filename = baseName + "_" + (i + 1) + ".jpg";
            Path filePath = outputDir.resolve(filename);

            if (Files.exists(filePath)) {
                System.out.println("Pixabay photo already exists → " + filePath);
                continue;
            }

            System.out.println("Pixabay downloading photo " + (i + 1) + " for " + spot.getName());
            System.out.println("   URL = " + imageUrl);

            byte[] bytes = webClient.get()
                    .uri(imageUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (bytes != null && bytes.length > 0) {
                Files.write(filePath, bytes);
                System.out.println("Saved Pixabay photo → " + filePath
                        + " (" + bytes.length + " bytes)");
            } else {
                System.out.println("Empty Pixabay photo data for " + filename);
            }
        }
    }

    /**
     * Build a reasonably specific query for Pixabay:
     * "spot, city, country" with diacritics removed.
     */
    private String buildQuery(Spot spot, City city, Country country) {
        String s = normalizeName(spot.getName());
        String c = normalizeName(city.getName());
        String cn = normalizeName(country.getName());
        return s + ", " + c + ", " + cn;
    }

    /**
     * Make a safe file name from spot name (no weird characters).
     */
    private String normalizeFileName(String spotName) {
        if (spotName == null) return "spot";
        return spotName
                .trim()
                .replaceAll("[^a-zA-Z0-9]+", "_");
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        // remove combining diacritical marks
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
