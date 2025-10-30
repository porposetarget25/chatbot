package com.example.genai.rag;

import com.example.genai.util.Cosine;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class EmbeddingStore {
    private final List<TextChunk> store = new CopyOnWriteArrayList<>();

    public void upsert(TextChunk chunk) {
        store.removeIf(c -> c.id().equals(chunk.id()));
        store.add(chunk);
    }

    public List<TextChunk> topK(float[] query, int k) {
        return store.stream()
                .sorted((a,b) -> Float.compare(
                        Cosine.similarity(b.embedding(), query),
                        Cosine.similarity(a.embedding(), query)
                ))
                .limit(k)
                .toList();
    }
}
