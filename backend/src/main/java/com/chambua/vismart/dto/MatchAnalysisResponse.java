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
}
