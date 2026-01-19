package com.example.genai.component;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMemoryStore {

    private final Map<String, List<Map<String, String>>> store = new ConcurrentHashMap<>();
    private final int maxMessages = 30; // keep last N (avoid token explosion)

    public List<Map<String, String>> get(String sessionId) {
        return new ArrayList<>(store.getOrDefault(sessionId, List.of()));
    }

    public void append(String sessionId, Map<String, String> message) {
        store.compute(sessionId, (k, v) -> {
            List<Map<String, String>> list = (v == null) ? new ArrayList<>() : new ArrayList<>(v);
            list.add(message);

            // Trim oldest to cap context size
            if (list.size() > maxMessages) {
                list = list.subList(list.size() - maxMessages, list.size());
            }
            return list;
        });
    }

    public void clear(String sessionId) {
        store.remove(sessionId);
    }
}

