package com.chambua.vismart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "teams", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_name_league", columnNames = {"name", "league_id"})
})
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false, foreignKey = @ForeignKey(name = "fk_team_league"))
    private League league;

    public Team() {}

    public Team(String name, League league) {
        this.name = name;
        this.league = league;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public League getLeague() { return league; }
    public void setLeague(League league) { this.league = league; }
}
