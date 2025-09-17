package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "persisted_fixture_analyses",
        uniqueConstraints = @UniqueConstraint(name = "uk_persisted_fixture_date",
                columnNames = {"analysis_date", "league_id", "home_team_id", "away_team_id"}),
        indexes = {
                @Index(name = "idx_analysis_date", columnList = "analysis_date"),
                @Index(name = "idx_analysis_date_league", columnList = "analysis_date,league_id")
        })
public class PersistedFixtureAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "season_id")
    private Long seasonId;

    @Column(name = "home_team_id", nullable = false)
    private Long homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    private Long awayTeamId;

    @Column(name = "home_team_name", nullable = false)
    private String homeTeamName;

    @Column(name = "away_team_name", nullable = false)
    private String awayTeamName;

    @Column(name = "fixture_id")
    private Long fixtureId;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "user_id")
    private String userId;

    @Lob
    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AnalysisSource { INDIVIDUAL, BATCH }

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AnalysisSource source;

    public PersistedFixtureAnalysis() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }
    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }
    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }
    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }
    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public LocalDate getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(LocalDate analysisDate) { this.analysisDate = analysisDate; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public AnalysisSource getSource() { return source; }
    public void setSource(AnalysisSource source) { this.source = source; }

    // Simple builder
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final PersistedFixtureAnalysis inst = new PersistedFixtureAnalysis();
        public Builder leagueId(Long v){ inst.setLeagueId(v); return this; }
        public Builder seasonId(Long v){ inst.setSeasonId(v); return this; }
        public Builder homeTeamId(Long v){ inst.setHomeTeamId(v); return this; }
        public Builder awayTeamId(Long v){ inst.setAwayTeamId(v); return this; }
        public Builder homeTeamName(String v){ inst.setHomeTeamName(v); return this; }
        public Builder awayTeamName(String v){ inst.setAwayTeamName(v); return this; }
        public Builder fixtureId(Long v){ inst.setFixtureId(v); return this; }
        public Builder analysisDate(LocalDate v){ inst.setAnalysisDate(v); return this; }
        public Builder userId(String v){ inst.setUserId(v); return this; }
        public Builder resultJson(String v){ inst.setResultJson(v); return this; }
        public Builder createdAt(Instant v){ inst.setCreatedAt(v); return this; }
        public Builder updatedAt(Instant v){ inst.setUpdatedAt(v); return this; }
        public Builder source(AnalysisSource v){ inst.setSource(v); return this; }
        public PersistedFixtureAnalysis build(){ return inst; }
    }
}
