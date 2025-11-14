package com.example.genai.entity;
import jakarta.persistence.*;

@Entity @Table(name="city", uniqueConstraints=@UniqueConstraint(columnNames={"country_id","name"}))
public class City {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) Country country;
    @Column(nullable=false) String name;

    public City(Long id, Country country, String name) {
        this.id = id;
        this.country = country;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
