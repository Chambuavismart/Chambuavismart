package com.chambua.vismart.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AdminLeagueSummaryDTO {
    public Long leagueId;
    public String name;
    public String category; // not in schema; placeholder
    public String country;
    public List<SeasonItem> seasons;
    public Long currentSeasonId;
    public String currentSeasonName;
    public Instant lastUpdatedAt; // last time current season was updated/imported

    public static class SeasonItem {
        public Long id;
        public String name;
        public LocalDate startDate;
        public LocalDate endDate;

        public SeasonItem() {}
        public SeasonItem(Long id, String name, LocalDate startDate, LocalDate endDate) {
            this.id = id;
            this.name = name;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    public AdminLeagueSummaryDTO() {}

    public AdminLeagueSummaryDTO(Long leagueId, String name, String category, String country, List<SeasonItem> seasons,
                                 Long currentSeasonId, String currentSeasonName, Instant lastUpdatedAt) {
        this.leagueId = leagueId;
        this.name = name;
        this.category = category;
        this.country = country;
        this.seasons = seasons;
        this.currentSeasonId = currentSeasonId;
        this.currentSeasonName = currentSeasonName;
        this.lastUpdatedAt = lastUpdatedAt;
    }
}
