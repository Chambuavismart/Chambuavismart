package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_matches_league_round", columnList = "league_id, round"),
        @Index(name = "idx_matches_date", columnList = "match_date"),
        @Index(name = "idx_matches_season_teams_date", columnList = "season_id, home_team_id, away_team_id, match_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_league_round_home_away", columnNames = {"league_id", "round", "home_team_id", "away_team_id"}),
        @UniqueConstraint(name = "uk_match_season_home_away_date", columnNames = {"season_id", "home_team_id", "away_team_id", "match_date"})
})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false, foreignKey = @ForeignKey(name = "fk_match_league"))
    private League league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = true, foreignKey = @ForeignKey(name = "fk_match_season"))
    private Season season; // now mandatory; database enforces NOT NULL

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_match_home_team"))
    private Team homeTeam;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false, foreignKey = @ForeignKey(name = "fk_match_away_team"))
    private Team awayTeam;

    @Column(name = "match_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer round;

    @Column(nullable = true)
    private Integer homeGoals;

    @Column(nullable = true)
    private Integer awayGoals;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchStatus status = MatchStatus.SCHEDULED;

    public Match() {}

    public Match(League league, Team homeTeam, Team awayTeam, LocalDate date, Integer round, Integer homeGoals, Integer awayGoals) {
        this.league = league;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.date = date;
        this.round = round;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        // derive status based on goals if provided
        if (homeGoals != null && awayGoals != null) {
            this.status = MatchStatus.PLAYED;
        } else {
            this.status = MatchStatus.SCHEDULED;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public League getLeague() { return league; }
    public void setLeague(League league) { this.league = league; }

    public Season getSeason() { return season; }
    public void setSeason(Season season) { this.season = season; }

    public Team getHomeTeam() { return homeTeam; }
    public void setHomeTeam(Team homeTeam) { this.homeTeam = homeTeam; }

    public Team getAwayTeam() { return awayTeam; }
    public void setAwayTeam(Team awayTeam) { this.awayTeam = awayTeam; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }

    public Integer getHomeGoals() { return homeGoals; }
    public void setHomeGoals(Integer homeGoals) { this.homeGoals = homeGoals; }

    public Integer getAwayGoals() { return awayGoals; }
    public void setAwayGoals(Integer awayGoals) { this.awayGoals = awayGoals; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
}
