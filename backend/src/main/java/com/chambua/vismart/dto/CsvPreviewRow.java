package com.chambua.vismart.dto;

import java.util.List;

public class CsvPreviewRow {
    private int line;
    private List<String> values;
    private String status; // ok | error | warn
    private String reason; // optional

    public CsvPreviewRow() {}

    public CsvPreviewRow(int line, List<String> values, String status, String reason) {
        this.line = line;
        this.values = values;
        this.status = status;
        this.reason = reason;
    }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
