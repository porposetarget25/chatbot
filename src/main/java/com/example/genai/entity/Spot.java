package com.example.genai.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "spot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"city_id", "name"})
)
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false)
    private String name; // This is your {spot}

    public Spot(Long id, City city, String name) {
        this.id = id;
        this.city = city;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
