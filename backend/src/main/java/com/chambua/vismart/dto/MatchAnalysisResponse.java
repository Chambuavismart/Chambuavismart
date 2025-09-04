package com.chambua.vismart.dto;

public class MatchAnalysisResponse {
    private String homeTeam;
    private String awayTeam;
    private String league;
    private WinProbabilities winProbabilities;
    private int bttsProbability;
    private int over25Probability;
    private ExpectedGoals expectedGoals;
    private int confidenceScore;
    private String advice;
    // Optional: expose unblended form stats and H2H summary for UI visualization
    private FormSummary formSummary; // may be null
    private H2HSummary h2hSummary;   // may be null

    public MatchAnalysisResponse() {}

    public MatchAnalysisResponse(String homeTeam, String awayTeam, String league, WinProbabilities winProbabilities,
                                 int bttsProbability, int over25Probability, ExpectedGoals expectedGoals,
                                 int confidenceScore, String advice) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.league = league;
        this.winProbabilities = winProbabilities;
        this.bttsProbability = bttsProbability;
        this.over25Probability = over25Probability;
        this.expectedGoals = expectedGoals;
        this.confidenceScore = confidenceScore;
        this.advice = advice;
    }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }
    public WinProbabilities getWinProbabilities() { return winProbabilities; }
    public void setWinProbabilities(WinProbabilities winProbabilities) { this.winProbabilities = winProbabilities; }
    public int getBttsProbability() { return bttsProbability; }
    public void setBttsProbability(int bttsProbability) { this.bttsProbability = bttsProbability; }
    public int getOver25Probability() { return over25Probability; }
    public void setOver25Probability(int over25Probability) { this.over25Probability = over25Probability; }
    public ExpectedGoals getExpectedGoals() { return expectedGoals; }
    public void setExpectedGoals(ExpectedGoals expectedGoals) { this.expectedGoals = expectedGoals; }
    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }
    public FormSummary getFormSummary() { return formSummary; }
    public void setFormSummary(FormSummary formSummary) { this.formSummary = formSummary; }
    public H2HSummary getH2hSummary() { return h2hSummary; }
    public void setH2hSummary(H2HSummary h2hSummary) { this.h2hSummary = h2hSummary; }

    public static class WinProbabilities {
        private int homeWin;
        private int draw;
        private int awayWin;
        public WinProbabilities() {}
        public WinProbabilities(int homeWin, int draw, int awayWin) {
            this.homeWin = homeWin; this.draw = draw; this.awayWin = awayWin;
        }
        public int getHomeWin() { return homeWin; }
        public void setHomeWin(int homeWin) { this.homeWin = homeWin; }
        public int getDraw() { return draw; }
        public void setDraw(int draw) { this.draw = draw; }
        public int getAwayWin() { return awayWin; }
        public void setAwayWin(int awayWin) { this.awayWin = awayWin; }
    }

    public static class ExpectedGoals {
        private double home;
        private double away;
        public ExpectedGoals() {}
        public ExpectedGoals(double home, double away) { this.home = home; this.away = away; }
        public double getHome() { return home; }
        public void setHome(double home) { this.home = home; }
        public double getAway() { return away; }
        public void setAway(double away) { this.away = away; }
    }

    // Base unblended stats derived from team form (before H2H and league adjustments)
    public static class FormSummary {
        private int homeWin;
        private int draw;
        private int awayWin;
        private int btts;
        private int over25;
        public FormSummary() {}
        public FormSummary(int homeWin, int draw, int awayWin, int btts, int over25) {
            this.homeWin = homeWin; this.draw = draw; this.awayWin = awayWin; this.btts = btts; this.over25 = over25;
        }
        public int getHomeWin() { return homeWin; }
        public void setHomeWin(int homeWin) { this.homeWin = homeWin; }
        public int getDraw() { return draw; }
        public void setDraw(int draw) { this.draw = draw; }
        public int getAwayWin() { return awayWin; }
        public void setAwayWin(int awayWin) { this.awayWin = awayWin; }
        public int getBtts() { return btts; }
        public void setBtts(int btts) { this.btts = btts; }
        public int getOver25() { return over25; }
        public void setOver25(int over25) { this.over25 = over25; }
    }

    // Recency-weighted H2H metrics and last N window used
    public static class H2HSummary {
        private int lastN;
        private double ppgHome;
        private double ppgAway;
        private int bttsPct;
        private int over25Pct;
        public H2HSummary() {}
        public H2HSummary(int lastN, double ppgHome, double ppgAway, int bttsPct, int over25Pct) {
            this.lastN = lastN; this.ppgHome = ppgHome; this.ppgAway = ppgAway; this.bttsPct = bttsPct; this.over25Pct = over25Pct;
        }
        public int getLastN() { return lastN; }
        public void setLastN(int lastN) { this.lastN = lastN; }
        public double getPpgHome() { return ppgHome; }
        public void setPpgHome(double ppgHome) { this.ppgHome = ppgHome; }
        public double getPpgAway() { return ppgAway; }
        public void setPpgAway(double ppgAway) { this.ppgAway = ppgAway; }
        public int getBttsPct() { return bttsPct; }
        public void setBttsPct(int bttsPct) { this.bttsPct = bttsPct; }
        public int getOver25Pct() { return over25Pct; }
        public void setOver25Pct(int over25Pct) { this.over25Pct = over25Pct; }
    }
}
