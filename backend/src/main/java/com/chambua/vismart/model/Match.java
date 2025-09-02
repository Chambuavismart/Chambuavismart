package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_matches_league_round", columnList = "league_id, round"),
        @Index(name = "idx_matches_date", columnList = "match_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_league_round_home_away", columnNames = {"league_id", "round", "home_team_id", "away_team_id"})
})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false, foreignKey = @ForeignKey(name = "fk_match_league"))
    private League league;

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

    @Column(nullable = false)
    private Integer homeGoals;

    @Column(nullable = false)
    private Integer awayGoals;

    public Match() {}

    public Match(League league, Team homeTeam, Team awayTeam, LocalDate date, Integer round, Integer homeGoals, Integer awayGoals) {
        this.league = league;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.date = date;
        this.round = round;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public League getLeague() { return league; }
    public void setLeague(League league) { this.league = league; }

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
}
