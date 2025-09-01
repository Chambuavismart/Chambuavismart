package com.chambua.vismart.dto;

import java.util.List;

public class FormGuideRowDTO {
    private Long teamId;
    private String teamName;
    private int w;
    private int d;
    private int l;
    private int gf;
    private int ga;
    private int gd;
    private int pts;
    private double ppg;
    private List<String> lastResults; // latest first, values: W/D/L
    private int bttsPct; // 0-100
    private int over15Pct; // 0-100
    private int over25Pct; // 0-100

    public FormGuideRowDTO() {}

    public FormGuideRowDTO(Long teamId, String teamName, int w, int d, int l, int gf, int ga, int pts,
                           double ppg, List<String> lastResults, int bttsPct, int over15Pct, int over25Pct) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.w = w;
        this.d = d;
        this.l = l;
        this.gf = gf;
        this.ga = ga;
        this.gd = gf - ga;
        this.pts = pts;
        this.ppg = ppg;
        this.lastResults = lastResults;
        this.bttsPct = bttsPct;
        this.over15Pct = over15Pct;
        this.over25Pct = over25Pct;
    }

    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public int getW() { return w; }
    public int getD() { return d; }
    public int getL() { return l; }
    public int getGf() { return gf; }
    public int getGa() { return ga; }
    public int getGd() { return gd; }
    public int getPts() { return pts; }
    public double getPpg() { return ppg; }
    public List<String> getLastResults() { return lastResults; }
    public int getBttsPct() { return bttsPct; }
    public int getOver15Pct() { return over15Pct; }
    public int getOver25Pct() { return over25Pct; }

    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public void setW(int w) { this.w = w; }
    public void setD(int d) { this.d = d; }
    public void setL(int l) { this.l = l; }
    public void setGf(int gf) { this.gf = gf; this.gd = this.gf - this.ga; }
    public void setGa(int ga) { this.ga = ga; this.gd = this.gf - this.ga; }
    public void setPts(int pts) { this.pts = pts; }
    public void setPpg(double ppg) { this.ppg = ppg; }
    public void setLastResults(List<String> lastResults) { this.lastResults = lastResults; }
    public void setBttsPct(int bttsPct) { this.bttsPct = bttsPct; }
    public void setOver15Pct(int over15Pct) { this.over15Pct = over15Pct; }
    public void setOver25Pct(int over25Pct) { this.over25Pct = over25Pct; }
}
