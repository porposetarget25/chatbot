package com.example.genai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public final class StreamJsonParsers {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StreamJsonParsers() {}

    public static List<String> extractDeltaContent(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return List.of();

            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) return List.of();

            JsonNode content = delta.get("content");
            if (content == null || content.isNull()) return List.of();

            String text = content.asText();
            return (text == null || text.isBlank()) ? List.of() : List.of(text);
        } catch (Exception e) {
            return List.of(); // swallow parse errors for robustness
        }
    }
}

