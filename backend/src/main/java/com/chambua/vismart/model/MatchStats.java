package com.chambua.vismart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "match_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_stats", columnNames = {"match_id"})
})
public class MatchStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", foreignKey = @ForeignKey(name = "fk_match_stats_match"))
    private Match match;

    @Column(columnDefinition = "json")
    private String stats;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Match getMatch() { return match; }
    public void setMatch(Match match) { this.match = match; }
    public String getStats() { return stats; }
    public void setStats(String stats) { this.stats = stats; }
}
