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
    private String content;     // LLM response

    private String model;
    private Integer tokensIn;
    private Integer tokensOut;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Response(Long id, Spot spot, Language language, String templateKey, String content, String model, Integer tokensIn, Integer tokensOut, Instant createdAt) {
        this.id = id;
        this.spot = spot;
        this.language = language;
        this.templateKey = templateKey;
        this.content = content;
        this.model = model;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.createdAt = createdAt;
    }

    public Response() {

    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
