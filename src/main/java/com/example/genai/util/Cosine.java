package com.example.genai.util;

public class Cosine {
    public static float similarity(float[] a, float[] b) {
        float dot = 0f, na = 0f, nb = 0f;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        if (na == 0 || nb == 0) return 0f;
        return (float)(dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}
