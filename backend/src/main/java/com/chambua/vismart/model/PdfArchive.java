package com.chambua.vismart.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pdf_archive", indexes = {
        @Index(name = "idx_pdf_archive_generated_at", columnList = "generatedAt"),
        @Index(name = "idx_pdf_archive_teams", columnList = "homeTeam,awayTeam")
})
public class PdfArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 260)
    private String filename;

    @Column(nullable = false, length = 120)
    private String homeTeam;

    @Column(nullable = false, length = 120)
    private String awayTeam;

    @Column(nullable = false)
    private Instant generatedAt;

    @Column(nullable = false, length = 80)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] bytes;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String requestSnapshot; // JSON of AnalysisRequest used

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public byte[] getBytes() { return bytes; }
    public void setBytes(byte[] bytes) { this.bytes = bytes; }

    public String getRequestSnapshot() { return requestSnapshot; }
    public void setRequestSnapshot(String requestSnapshot) { this.requestSnapshot = requestSnapshot; }
}
