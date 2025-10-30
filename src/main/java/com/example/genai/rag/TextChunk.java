package com.example.genai.rag;

public record TextChunk(String id, String text, float[] embedding) {}
