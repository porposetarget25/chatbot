package com.example.genai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "prompt_template",
        uniqueConstraints = @UniqueConstraint(columnNames = "prompt_key")
)
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Property name can still be "key", but column name must NOT be "key"
    @Column(name = "prompt_key", nullable = false, unique = true, length = 100)
    private String key;

    @Lob
    @Column(name = "prompt_text", nullable = false)
    private String text;

    public PromptTemplate() {}

    public PromptTemplate(Long id, String key, String text) {
        this.id = id;
        this.key = key;
        this.text = text;
    }

    // getters & setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
