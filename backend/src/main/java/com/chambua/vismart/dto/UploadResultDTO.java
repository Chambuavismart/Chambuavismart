package com.chambua.vismart.dto;

import java.util.ArrayList;
import java.util.List;

public class UploadResultDTO {
    private boolean success;
    private int inserted;
    private long deleted;
    private String message;
    private List<String> errors = new ArrayList<>();

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

    public void setSuccess(boolean success) { this.success = success; }
    public void setInserted(int inserted) { this.inserted = inserted; }
    public void setDeleted(long deleted) { this.deleted = deleted; }
    public void setMessage(String message) { this.message = message; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}