package com.example.genai.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "response",
        uniqueConstraints = @UniqueConstraint(columnNames = {"spot_id", "language_id", "template_key"})
)
public class Response {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey; // e.g. "TRAVEL_DESC_V1"

    @Lob
    @Column(nullable = false)
    private String content;     // LLM response (text)

    // Model used for text generation (e.g. gpt-4o-mini)
    private String model;

    // ✅ TEXT token usage
    // Treat these as "text input tokens" and "text output tokens"
    @Column(name = "text_tokens_in")
    private Integer tokensIn;   // prompt tokens for text

    @Column(name = "text_tokens_out")
    private Integer tokensOut;  // completion tokens for text

    // ✅ AUDIO token usage (for TTS)
    @Column(name = "audio_tokens_in")
    private Integer audioTokensIn;   // prompt tokens (or chars count) for audio

    @Column(name = "audio_tokens_out")
    private Integer audioTokensOut;  // completion tokens for audio (if available)

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ✅ S3 key (path in bucket) for the mixed audio
    @Column(length = 500)
    private String audioS3Key;

    public Response(Long id,
                    Spot spot,
                    Language language,
                    String templateKey,
                    String content,
                    String model,
                    Integer tokensIn,
                    Integer tokensOut,
                    Integer audioTokensIn,
                    Integer audioTokensOut,
                    Instant createdAt,
                    String audioS3Key) {
        this.id = id;
        this.spot = spot;
        this.language = language;
        this.templateKey = templateKey;
        this.content = content;
        this.model = model;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.audioTokensIn = audioTokensIn;
        this.audioTokensOut = audioTokensOut;
        this.createdAt = createdAt;
        this.audioS3Key = audioS3Key;
    }

    public Response() {
    }

    // --- getters & setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Spot getSpot() {
        return spot;
    }

    public void setSpot(Spot spot) {
        this.spot = spot;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    // TEXT tokens
    public Integer getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(Integer tokensIn) {
        this.tokensIn = tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(Integer tokensOut) {
        this.tokensOut = tokensOut;
    }

    // AUDIO tokens
    public Integer getAudioTokensIn() {
        return audioTokensIn;
    }

    public void setAudioTokensIn(Integer audioTokensIn) {
        this.audioTokensIn = audioTokensIn;
    }

    public Integer getAudioTokensOut() {
        return audioTokensOut;
    }

    public void setAudioTokensOut(Integer audioTokensOut) {
        this.audioTokensOut = audioTokensOut;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getAudioS3Key() {
        return audioS3Key;
    }

    public void setAudioS3Key(String audioS3Key) {
        this.audioS3Key = audioS3Key;
    }
}
