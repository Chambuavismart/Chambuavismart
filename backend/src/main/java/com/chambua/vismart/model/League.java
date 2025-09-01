package com.chambua.vismart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "leagues", uniqueConstraints = {
        @UniqueConstraint(name = "uk_league_name_country_season", columnNames = {"name", "country", "season"})
})
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String season; // e.g., 2024/2025 or 2025

    public League() {}

    public League(String name, String country, String season) {
        this.name = name;
        this.country = country;
        this.season = season;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
}
