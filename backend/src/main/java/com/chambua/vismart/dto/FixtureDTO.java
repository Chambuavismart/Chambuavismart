package com.chambua.vismart.dto;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;

import java.time.LocalDateTime;

public class FixtureDTO {
    private Long id;
    private String round;
    private LocalDateTime dateTime;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private FixtureStatus status;

    public static FixtureDTO from(Fixture f) {
        FixtureDTO dto = new FixtureDTO();
        dto.id = f.getId();
        dto.round = f.getRound();
        dto.dateTime = f.getDateTime();
        dto.homeTeam = f.getHomeTeam();
        dto.awayTeam = f.getAwayTeam();
        dto.homeScore = f.getHomeScore();
        dto.awayScore = f.getAwayScore();
        dto.status = f.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public String getRound() { return round; }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public Integer getHomeScore() { return homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public FixtureStatus getStatus() { return status; }
}
