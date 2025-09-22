package com.chambua.vismart.model;

import com.chambua.vismart.util.TeamNameNormalizer;
import jakarta.persistence.*;

@Entity
@Table(name = "teams", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_normalized_league", columnNames = {"normalized_name", "league_id"})
}, indexes = {
        @Index(name = "idx_team_league_name", columnList = "league_id, name")
})
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false, foreignKey = @ForeignKey(name = "fk_team_league"))
    private League league;

    public Team() {}

    public Team(String name, League league) {
        this.name = name;
        this.league = league;
        this.normalizedName = TeamNameNormalizer.normalize(name);
    }

    @PrePersist
    @PreUpdate
    private void prePersistUpdate() {
        if (this.name != null) {
            // Store canonical team name as lowercase without leading/trailing spaces
            this.name = this.name.trim().toLowerCase();
        }
        // Keep normalizedName aligned with current name (collapsed spaces + lowercase)
        this.normalizedName = TeamNameNormalizer.normalize(this.name);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }

    public League getLeague() { return league; }
    public void setLeague(League league) { this.league = league; }
}
