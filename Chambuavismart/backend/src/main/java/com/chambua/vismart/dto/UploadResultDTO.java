package com.chambua.vismart.dto;

import java.util.ArrayList;
import java.util.List;

public class UploadResultDTO {
    private boolean success;
    private int inserted;
    private long deleted;
    private String message;
    private List<String> errors = new ArrayList<>();

    // Enhancements for richer feedback
    private int processedMatches; // number of parsed/processed blocks or rows
    private List<String> warnings = new ArrayList<>();
    private List<String> ignoredLines = new ArrayList<>(); // sample of ignored lines (preprocessing)

    public static UploadResultDTO ok(int inserted, long deleted, String message){
        UploadResultDTO r = new UploadResultDTO();
        r.success = true;
        r.inserted = inserted;
        r.deleted = deleted;
        r.message = message;
        return r;
    }
    public static UploadResultDTO fail(String message, List<String> errors){
        UploadResultDTO r = new UploadResultDTO();
        r.success = false;
        r.message = message;
        if (errors != null) r.errors = errors;
        return r;
    }

    public boolean isSuccess() { return success; }
    public int getInserted() { return inserted; }
    public long getDeleted() { return deleted; }
    public String getMessage() { return message; }
    public List<String> getErrors() { return errors; }

    public int getProcessedMatches() { return processedMatches; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getIgnoredLines() { return ignoredLines; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setInserted(int inserted) { this.inserted = inserted; }
    public void setDeleted(long deleted) { this.deleted = deleted; }
    public void setMessage(String message) { this.message = message; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public void setProcessedMatches(int processedMatches) { this.processedMatches = processedMatches; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public void setIgnoredLines(List<String> ignoredLines) { this.ignoredLines = ignoredLines; }
}