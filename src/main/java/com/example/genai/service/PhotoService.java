package com.example.genai.service;

import com.example.genai.entity.City;
import com.example.genai.entity.Country;
import com.example.genai.entity.Spot;
import com.example.genai.repo.SpotRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

@Service
public class PhotoService {

    private final SpotRepo spotRepo;
    private final WebClient webClient;
    private final String googleApiKey;

    // Where to save the images
    private final Path outputDir = Paths.get(
            "C:\\Users\\mohahama\\Downloads\\World-Traveler\\generated_photos"
    );

    public PhotoService(
            SpotRepo spotRepo,
            WebClient.Builder webClientBuilder,
            @Value("${app.google.api-key}") String googleApiKey
    ) {
        this.spotRepo = spotRepo;
        this.googleApiKey = googleApiKey;

        // Base client for Places API (New)
        this.webClient = webClientBuilder
                .baseUrl("https://places.googleapis.com")
                .build();
    }

    /**
     * Downloads up to 20 photos for every Spot in DB.
     */
    @Transactional(readOnly = true)
    public int downloadPhotosForAllSpots() {
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
                System.err.println("Failed to download photos for spot: "
                        + spot.getName() + " – " + ex.getMessage());
            }
        }

        return spots.size();
    }

    /**
     * Use Places API (New) searchText to find a place + photos,
     * then call the media endpoint and follow 302 redirects to get actual bytes.
     */
    private void downloadPhotosForSpot(Spot spot) throws IOException {
        City city = spot.getCity();
        Country country = city.getCountry();

        String query = spot.getName() + ", " + city.getName() + ", " + country.getName();
        System.out.println("Searching photos for: " + query);

        Map<String, Object> response = webClient.post()
                .uri("/v1/places:searchText")
                .header("X-Goog-Api-Key", googleApiKey)
                .header("X-Goog-FieldMask", "places.name,places.photos.name")
                .bodyValue(Map.of("textQuery", query))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            System.out.println("No response from Places API for: " + query);
            return;
        }

        Object placesObj = response.get("places");
        if (!(placesObj instanceof List<?> places) || places.isEmpty()) {
            System.out.println("No candidates found for: " + query);
            return;
        }

        Object firstPlaceObj = places.get(0);
        if (!(firstPlaceObj instanceof Map<?, ?> firstPlace)) {
            System.out.println("Invalid place data for: " + query);
            return;
        }

        Object photosObj = firstPlace.get("photos");
        if (!(photosObj instanceof List<?> photos) || photos.isEmpty()) {
            System.out.println("No photos found for: " + query);
            return;
        }

        int max = Math.min(20, photos.size());
        String baseName = normalizeFileName(spot.getName());

        for (int i = 0; i < max; i++) {
            Object photoObj = photos.get(i);
            if (!(photoObj instanceof Map<?, ?> photoMap)) {
                continue;
            }

            Object nameObj = photoMap.get("name");
            if (!(nameObj instanceof String photoName) || photoName.isBlank()) {
                continue;
            }

            Path filePath = outputDir.resolve(baseName + "_" + (i + 1) + ".jpg");
            if (Files.exists(filePath)) {
                System.out.println("Photo already exists → " + filePath);
                continue;
            }

            System.out.println("Downloading photo " + (i + 1) + " for " + spot.getName());
            System.out.println("  photoName = " + photoName);

            byte[] bytes = fetchPhotoBytes(photoName);

            if (bytes != null && bytes.length > 0) {
                Files.write(filePath, bytes);
                System.out.println("Saved photo → " + filePath + " (" + bytes.length + " bytes)");
            } else {
                System.out.println("Empty photo data for " + filePath);
            }
        }
    }


    private byte[] fetchPhotoBytes(String photoName) {
        ClientResponse initial = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/" + photoName + "/media")
                        .queryParam("maxHeightPx", 1600)
                        .queryParam("maxWidthPx", 1600)
                        .build())
                .header("X-Goog-Api-Key", googleApiKey)
                .exchangeToMono(Mono::just)
                .block();

        if (initial == null) {
            System.out.println("Photo API returned null response");
            return null;
        }

        HttpStatus status = (HttpStatus) initial.statusCode();

        if (status.is2xxSuccessful()) {
            return initial.bodyToMono(byte[].class).block();
        }


        if (status.is3xxRedirection()) {
            String location = initial.headers()
                    .asHttpHeaders()
                    .getLocation() != null
                    ? initial.headers().asHttpHeaders().getLocation().toString()
                    : null;

            if (location == null || location.isBlank()) {
                System.out.println("Photo API redirect without Location header");
                return null;
            }

            System.out.println("Following redirect to: " + location);

            // Second GET: actual image URL (no Places headers needed here)
            return webClient.get()
                    .uri(location)
                    .exchangeToMono(resp -> {
                        if (resp.statusCode().is2xxSuccessful()) {
                            return resp.bodyToMono(byte[].class);
                        } else {
                            return resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> {
                                        System.out.println("Redirected image GET failed: "
                                                + resp.statusCode()
                                                + " body=" + body);
                                        return null;
                                    });
                        }
                    })
                    .block();
        }

        // Everything else → log and return null
        String body = initial.bodyToMono(String.class).blockOptional().orElse("<no body>");
        String ct = initial.headers().contentType().map(Object::toString).orElse("<unknown>");
        System.out.println("Photo API non-OK: " + status + " CT=" + ct);
        System.out.println("Body: " + body);
        return null;
    }

    private String normalizeFileName(String spotName) {
        if (spotName == null) return "spot";
        String normalized = Normalizer.normalize(spotName, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.trim().replaceAll("[^a-zA-Z0-9]+", "_");
    }
}
