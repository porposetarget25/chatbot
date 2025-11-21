package com.example.genai.entity;
import jakarta.persistence.*;

@Entity @Table(name="language", uniqueConstraints=@UniqueConstraint(columnNames={"code"}))
public class Language {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true, length=16) String code; // e.g., "en","hi","es"
    @Column(nullable=false) String name; // "English", "Hindi", …

    public Language(Long id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public Language() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}