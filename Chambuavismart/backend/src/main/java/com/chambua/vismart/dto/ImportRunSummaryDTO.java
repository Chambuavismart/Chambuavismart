package com.chambua.vismart.dto;

import java.time.Instant;

public class ImportRunSummaryDTO {
    private Long id;
    private String status;
    private Integer rowsTotal;
    private Integer rowsSuccess;
    private Integer rowsFailed;
    private String provider;
    private String competitionCode;
    private String filename;
    private String createdBy;
    private Instant startedAt;
    private Instant finishedAt;

    public ImportRunSummaryDTO() {}

    public ImportRunSummaryDTO(Long id, String status, Integer rowsTotal, Integer rowsSuccess, Integer rowsFailed, String provider, String competitionCode, String filename, String createdBy, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.status = status;
        this.rowsTotal = rowsTotal;
        this.rowsSuccess = rowsSuccess;
        this.rowsFailed = rowsFailed;
        this.provider = provider;
        this.competitionCode = competitionCode;
        this.filename = filename;
        this.createdBy = createdBy;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRowsTotal() { return rowsTotal; }
    public void setRowsTotal(Integer rowsTotal) { this.rowsTotal = rowsTotal; }
    public Integer getRowsSuccess() { return rowsSuccess; }
    public void setRowsSuccess(Integer rowsSuccess) { this.rowsSuccess = rowsSuccess; }
    public Integer getRowsFailed() { return rowsFailed; }
    public void setRowsFailed(Integer rowsFailed) { this.rowsFailed = rowsFailed; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getCompetitionCode() { return competitionCode; }
    public void setCompetitionCode(String competitionCode) { this.competitionCode = competitionCode; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
