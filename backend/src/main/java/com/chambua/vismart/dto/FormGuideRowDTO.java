package com.chambua.vismart.dto;

import java.util.List;

public class FormGuideRowDTO {
    private Long teamId;
    private String teamName;
    private int mp; // matches played in the considered window
    private int totalMp; // total matches played so far this season (per scope)
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
    private int over35Pct; // 0-100
    // Weighted goal rates (recency-weighted averages per match)
    private Double avgGfWeighted; // nullable to allow backward compatibility in tests
    private Double avgGaWeighted;

    // New: Home/Away recency-weighted splits
    private double weightedHomeGoalsFor;
    private double weightedHomeGoalsAgainst;
    private double weightedAwayGoalsFor;
    private double weightedAwayGoalsAgainst;
    private double weightedHomePPG;
    private double weightedAwayPPG;
    private int weightedHomeBTTSPercent;
    private int weightedAwayBTTSPercent;
    private int weightedHomeOver25Percent;
    private int weightedAwayOver25Percent;
    // Added: Over 1.5 and Over 3.5 home/away percentages
    private int weightedHomeOver15Percent;
    private int weightedAwayOver15Percent;
    private int weightedHomeOver35Percent;
    private int weightedAwayOver35Percent;
    // New: window sizes used for home/away splits (number of matches considered)
    private int weightedHomeMatches;
    private int weightedAwayMatches;

    public FormGuideRowDTO() {}

    public FormGuideRowDTO(Long teamId, String teamName, int mp, int totalMp, int w, int d, int l, int gf, int ga, int pts,
                           double ppg, List<String> lastResults, int bttsPct, int over15Pct, int over25Pct, int over35Pct) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.mp = mp;
        this.totalMp = totalMp;
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
        this.over35Pct = over35Pct;
    }

    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public int getMp() { return mp; }
    public int getTotalMp() { return totalMp; }
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
    public int getOver35Pct() { return over35Pct; }
    public Double getAvgGfWeighted() { return avgGfWeighted; }
    public Double getAvgGaWeighted() { return avgGaWeighted; }

    public double getWeightedHomeGoalsFor() { return weightedHomeGoalsFor; }
    public double getWeightedHomeGoalsAgainst() { return weightedHomeGoalsAgainst; }
    public double getWeightedAwayGoalsFor() { return weightedAwayGoalsFor; }
    public double getWeightedAwayGoalsAgainst() { return weightedAwayGoalsAgainst; }
    public double getWeightedHomePPG() { return weightedHomePPG; }
    public double getWeightedAwayPPG() { return weightedAwayPPG; }
    public int getWeightedHomeBTTSPercent() { return weightedHomeBTTSPercent; }
    public int getWeightedAwayBTTSPercent() { return weightedAwayBTTSPercent; }
    public int getWeightedHomeOver25Percent() { return weightedHomeOver25Percent; }
    public int getWeightedAwayOver25Percent() { return weightedAwayOver25Percent; }
    public int getWeightedHomeOver15Percent() { return weightedHomeOver15Percent; }
    public int getWeightedAwayOver15Percent() { return weightedAwayOver15Percent; }
    public int getWeightedHomeOver35Percent() { return weightedHomeOver35Percent; }
    public int getWeightedAwayOver35Percent() { return weightedAwayOver35Percent; }
    public int getWeightedHomeMatches() { return weightedHomeMatches; }
    public int getWeightedAwayMatches() { return weightedAwayMatches; }

    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public void setMp(int mp) { this.mp = mp; }
    public void setTotalMp(int totalMp) { this.totalMp = totalMp; }
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
    public void setOver35Pct(int over35Pct) { this.over35Pct = over35Pct; }
    public void setAvgGfWeighted(Double avgGfWeighted) { this.avgGfWeighted = avgGfWeighted; }
    public void setAvgGaWeighted(Double avgGaWeighted) { this.avgGaWeighted = avgGaWeighted; }

    public void setWeightedHomeGoalsFor(double v) { this.weightedHomeGoalsFor = v; }
    public void setWeightedHomeGoalsAgainst(double v) { this.weightedHomeGoalsAgainst = v; }
    public void setWeightedAwayGoalsFor(double v) { this.weightedAwayGoalsFor = v; }
    public void setWeightedAwayGoalsAgainst(double v) { this.weightedAwayGoalsAgainst = v; }
    public void setWeightedHomePPG(double v) { this.weightedHomePPG = v; }
    public void setWeightedAwayPPG(double v) { this.weightedAwayPPG = v; }
    public void setWeightedHomeBTTSPercent(int v) { this.weightedHomeBTTSPercent = v; }
    public void setWeightedAwayBTTSPercent(int v) { this.weightedAwayBTTSPercent = v; }
    public void setWeightedHomeOver25Percent(int v) { this.weightedHomeOver25Percent = v; }
    public void setWeightedAwayOver25Percent(int v) { this.weightedAwayOver25Percent = v; }
    public void setWeightedHomeOver15Percent(int v) { this.weightedHomeOver15Percent = v; }
    public void setWeightedAwayOver15Percent(int v) { this.weightedAwayOver15Percent = v; }
    public void setWeightedHomeOver35Percent(int v) { this.weightedHomeOver35Percent = v; }
    public void setWeightedAwayOver35Percent(int v) { this.weightedAwayOver35Percent = v; }
    public void setWeightedHomeMatches(int v) { this.weightedHomeMatches = v; }
    public void setWeightedAwayMatches(int v) { this.weightedAwayMatches = v; }

    // Aliases for API compatibility with expected field names
    // Overall weighted metrics
    public double getWeightedPpg() { return getPpg(); }
    public int getWeightedBttsPct() { return getBttsPct(); }
    public int getWeightedOver15Pct() { return getOver15Pct(); }
    public int getWeightedOver25Pct() { return getOver25Pct(); }
    public int getWeightedOver35Pct() { return getOver35Pct(); }

    // Home/Away naming variants
    public double getWeightedPpgHome() { return getWeightedHomePPG(); }
    public double getWeightedPpgAway() { return getWeightedAwayPPG(); }
    public int getWeightedBttsHomePct() { return getWeightedHomeBTTSPercent(); }
    public int getWeightedBttsAwayPct() { return getWeightedAwayBTTSPercent(); }
    public int getWeightedOver15HomePct() { return getWeightedHomeOver15Percent(); }
    public int getWeightedOver15AwayPct() { return getWeightedAwayOver15Percent(); }
    public int getWeightedOver25HomePct() { return getWeightedHomeOver25Percent(); }
    public int getWeightedOver25AwayPct() { return getWeightedAwayOver25Percent(); }
    public int getWeightedOver35HomePct() { return getWeightedHomeOver35Percent(); }
    public int getWeightedOver35AwayPct() { return getWeightedAwayOver35Percent(); }
}
