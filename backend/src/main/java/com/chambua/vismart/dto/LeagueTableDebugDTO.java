package com.chambua.vismart.dto;

import java.util.List;

public class LeagueTableDebugDTO {
    public record DuplicateRow(Long leagueId, Long homeTeamId, Long awayTeamId, java.sql.Date matchDate, Integer round, Long count) {}
    public record PerTeamMp(Long teamId, String teamName, Long mp) {}

    private long totalRows;
    private long distinctFixtures;
    private List<DuplicateRow> duplicates; // limited list
    private List<PerTeamMp> perTeamMp;
    private List<LeagueTableEntryDTO> apiTable;

    public LeagueTableDebugDTO() {}

    public LeagueTableDebugDTO(long totalRows, long distinctFixtures, List<DuplicateRow> duplicates,
                               List<PerTeamMp> perTeamMp, List<LeagueTableEntryDTO> apiTable) {
        this.totalRows = totalRows;
        this.distinctFixtures = distinctFixtures;
        this.duplicates = duplicates;
        this.perTeamMp = perTeamMp;
        this.apiTable = apiTable;
    }

    public long getTotalRows() { return totalRows; }
    public void setTotalRows(long totalRows) { this.totalRows = totalRows; }

    public long getDistinctFixtures() { return distinctFixtures; }
    public void setDistinctFixtures(long distinctFixtures) { this.distinctFixtures = distinctFixtures; }

    public List<DuplicateRow> getDuplicates() { return duplicates; }
    public void setDuplicates(List<DuplicateRow> duplicates) { this.duplicates = duplicates; }

    public List<PerTeamMp> getPerTeamMp() { return perTeamMp; }
    public void setPerTeamMp(List<PerTeamMp> perTeamMp) { this.perTeamMp = perTeamMp; }

    public List<LeagueTableEntryDTO> getApiTable() { return apiTable; }
    public void setApiTable(List<LeagueTableEntryDTO> apiTable) { this.apiTable = apiTable; }
}
