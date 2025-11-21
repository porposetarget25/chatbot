package com.example.genai.llm;

public class TtsResult {
    private byte[] audio;
    private int promptTokens;
    private int completionTokens;

    public TtsResult(byte[] audio, int promptTokens, int completionTokens) {
        this.audio = audio;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public byte[] getAudio() { return audio; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
}

