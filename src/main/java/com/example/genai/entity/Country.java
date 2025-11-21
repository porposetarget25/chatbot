package com.example.genai.entity;

import jakarta.persistence.*;

// Country.java
@Entity
@Table(name="country", uniqueConstraints=@UniqueConstraint(columnNames={"name"}))
public class Country {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true) String name;

    public Country(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Country() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
