package com.example.genai.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "response",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"spot_id","language_id","prompt_key"})
        }
)
public class Response {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;

    @ManyToOne(optional=false, fetch=FetchType.LAZY) Spot spot;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) Language language;

    @Column(name = "prompt_key", nullable = false, length = 64)
    private String promptKey;  // "Text1"/"Text2"/"Text3"
    @Lob @Column(nullable=false) String content;

    String model;
    Integer tokensIn;
    Integer tokensOut;

    @Column(nullable=false)
    Instant createdAt = Instant.now();

    public Response(Long id, Spot spot, Language language, String promptKey, String content, String model, Integer tokensIn, Integer tokensOut, Instant createdAt) {
        this.id = id;
        this.spot = spot;
        this.language = language;
        this.promptKey = promptKey;
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

    public String getPromptKey() {
        return promptKey;
    }

    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
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
