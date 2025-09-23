package com.chambua.vismart.dto;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;

public class FixtureDTO {
    private Long id;
    private String round;
    // Serialize as ISO-8601 string to be Angular-friendly
    private String dateTime;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private FixtureStatus status;

    public static FixtureDTO from(Fixture f) {
        FixtureDTO dto = new FixtureDTO();
        dto.id = f.getId();
        dto.round = f.getRound();
        dto.dateTime = f.getDateTime() != null ? f.getDateTime().toString() : null; // e.g., 2025-09-23T06:04:00
        dto.homeTeam = f.getHomeTeam();
        dto.awayTeam = f.getAwayTeam();
        dto.homeScore = f.getHomeScore();
        dto.awayScore = f.getAwayScore();
        dto.status = f.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public String getRound() { return round; }
    public String getDateTime() { return dateTime; }
    public String getHomeTeam() { return homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public Integer getHomeScore() { return homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public FixtureStatus getStatus() { return status; }
}
