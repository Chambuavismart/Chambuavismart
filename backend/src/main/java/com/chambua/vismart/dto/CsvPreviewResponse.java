package com.chambua.vismart.dto;

import java.util.List;

public class CsvPreviewResponse {
    private List<String> headers;
    private List<CsvPreviewRow> rows;

    public CsvPreviewResponse() {}

    public CsvPreviewResponse(List<String> headers, List<CsvPreviewRow> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }

    public List<CsvPreviewRow> getRows() { return rows; }
    public void setRows(List<CsvPreviewRow> rows) { this.rows = rows; }
}
