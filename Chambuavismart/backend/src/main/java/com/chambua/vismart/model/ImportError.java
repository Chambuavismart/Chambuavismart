package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_error")
public class ImportError {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_run_id", foreignKey = @ForeignKey(name = "fk_import_error_run"))
    private ImportRun importRun;

    @Column(name = "row_num")
    private Integer rowNumber;

    @Column(columnDefinition = "json")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ImportRun getImportRun() { return importRun; }
    public void setImportRun(ImportRun importRun) { this.importRun = importRun; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
