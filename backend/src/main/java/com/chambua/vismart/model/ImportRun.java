package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_run", indexes = {
        @Index(name = "idx_importrun_filehash", columnList = "file_hash")
})
public class ImportRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_hash", length = 128, nullable = false)
    private String fileHash;

    @Column(length = 64)
    private String provider;

    @Column(name = "source_type", length = 32)
    private String sourceType; // e.g., CSV, TEXT (ingest source), not to confuse with Match.SourceType

    @Column(length = 255)
    private String filename;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(length = 100)
    private String createdBy; // user or system that initiated the run

    @Column(columnDefinition = "json")
    private String params;

    @Column(name = "rows_total")
    private Integer rowsTotal = 0;

    @Column(name = "rows_success")
    private Integer rowsSuccess = 0;

    @Column(name = "rows_failed")
    private Integer rowsFailed = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 32)
    private String status = "IN_PROGRESS";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public Integer getRowsTotal() { return rowsTotal; }
    public void setRowsTotal(Integer rowsTotal) { this.rowsTotal = rowsTotal; }
    public Integer getRowsSuccess() { return rowsSuccess; }
    public void setRowsSuccess(Integer rowsSuccess) { this.rowsSuccess = rowsSuccess; }
    public Integer getRowsFailed() { return rowsFailed; }
    public void setRowsFailed(Integer rowsFailed) { this.rowsFailed = rowsFailed; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
