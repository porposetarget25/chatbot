package com.example.genai.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "prompt_template",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"spot_id", "prompt_key"})
        }
)
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Column(name = "prompt_key", nullable = false, length = 64)
    private String promptKey;          // was: key

    @Lob
    @Column(name = "prompt_text", nullable = false)
    private String text;               // was: text (column)

    public PromptTemplate() {}

    public PromptTemplate(Long id, Spot spot, String promptKey, String text) {
        this.id = id;
        this.spot = spot;
        this.promptKey = promptKey;
        this.text = text;
    }

    // Getters & setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Spot getSpot() { return spot; }
    public void setSpot(Spot spot) { this.spot = spot; }

    public String getPromptKey() { return promptKey; }
    public void setPromptKey(String promptKey) { this.promptKey = promptKey; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
