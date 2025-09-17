package com.chambua.vismart.dto;

import java.util.List;

public class FixtureAnalysisResponse {
    private double[] winDrawLossProbs;
    private double bttsProbability;
    private double over15Probability;
    private double over25Probability;
    private double over35Probability;
    private double[] expectedGoals;
    private String notes;
    private List<CorrectScorePrediction> correctScores;
    private String homeTeamName;
    private String awayTeamName;

    public double[] getWinDrawLossProbs() { return winDrawLossProbs; }
    public void setWinDrawLossProbs(double[] winDrawLossProbs) { this.winDrawLossProbs = winDrawLossProbs; }

    public double getBttsProbability() { return bttsProbability; }
    public void setBttsProbability(double bttsProbability) { this.bttsProbability = bttsProbability; }

    public double getOver15Probability() { return over15Probability; }
    public void setOver15Probability(double over15Probability) { this.over15Probability = over15Probability; }

    public double getOver25Probability() { return over25Probability; }
    public void setOver25Probability(double over25Probability) { this.over25Probability = over25Probability; }

    public double getOver35Probability() { return over35Probability; }
    public void setOver35Probability(double over35Probability) { this.over35Probability = over35Probability; }

    public double[] getExpectedGoals() { return expectedGoals; }
    public void setExpectedGoals(double[] expectedGoals) { this.expectedGoals = expectedGoals; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<CorrectScorePrediction> getCorrectScores() { return correctScores; }
    public void setCorrectScores(List<CorrectScorePrediction> correctScores) { this.correctScores = correctScores; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public static class CorrectScorePrediction {
        private String score;
        private double probability;

        public CorrectScorePrediction() {}
        public CorrectScorePrediction(String score, double probability) {
            this.score = score;
            this.probability = probability;
        }
        public String getScore() { return score; }
        public void setScore(String score) { this.score = score; }
        public double getProbability() { return probability; }
        public void setProbability(double probability) { this.probability = probability; }
    }
}
