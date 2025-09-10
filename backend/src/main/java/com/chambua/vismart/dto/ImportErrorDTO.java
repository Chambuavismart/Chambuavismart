package com.chambua.vismart.dto;

public class ImportErrorDTO {
    private Long id;
    private Integer rowNumber;
    private String errorMessage;
    private String rawData;

    public ImportErrorDTO() {}

    public ImportErrorDTO(Long id, Integer rowNumber, String errorMessage, String rawData) {
        this.id = id;
        this.rowNumber = rowNumber;
        this.errorMessage = errorMessage;
        this.rawData = rawData;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }
}
