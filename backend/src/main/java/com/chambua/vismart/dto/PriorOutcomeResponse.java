package com.chambua.vismart.dto;

import java.util.List;

public class PriorOutcomeResponse {
    private String team;
    private List<PriorOutcomeStat> stats;

    public PriorOutcomeResponse() {}

    public PriorOutcomeResponse(String team, List<PriorOutcomeStat> stats) {
        this.team = team;
        this.stats = stats;
    }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public List<PriorOutcomeStat> getStats() { return stats; }
    public void setStats(List<PriorOutcomeStat> stats) { this.stats = stats; }

    public static class PriorOutcomeStat {
        private String priorResult;
        private String priorScoreLine;
        private NextResults nextResults;
        private int sampleSize; // number of next matches used for percentages

        public PriorOutcomeStat() {}

        public PriorOutcomeStat(String priorResult, String priorScoreLine, NextResults nextResults) {
            this.priorResult = priorResult;
            this.priorScoreLine = priorScoreLine;
            this.nextResults = nextResults;
        }

        public PriorOutcomeStat(String priorResult, String priorScoreLine, NextResults nextResults, int sampleSize) {
            this.priorResult = priorResult;
            this.priorScoreLine = priorScoreLine;
            this.nextResults = nextResults;
            this.sampleSize = sampleSize;
        }

        public String getPriorResult() { return priorResult; }
        public void setPriorResult(String priorResult) { this.priorResult = priorResult; }

        public String getPriorScoreLine() { return priorScoreLine; }
        public void setPriorScoreLine(String priorScoreLine) { this.priorScoreLine = priorScoreLine; }

        public NextResults getNextResults() { return nextResults; }
        public void setNextResults(NextResults nextResults) { this.nextResults = nextResults; }

        public int getSampleSize() { return sampleSize; }
        public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }
    }

    public static class NextResults {
        private double Win;
        private double Draw;
        private double Loss;

        public NextResults() {}
        public NextResults(double win, double draw, double loss) {
            this.Win = win; this.Draw = draw; this.Loss = loss;
        }

        public double getWin() { return Win; }
        public void setWin(double win) { Win = win; }
        public double getDraw() { return Draw; }
        public void setDraw(double draw) { Draw = draw; }
        public double getLoss() { return Loss; }
        public void setLoss(double loss) { Loss = loss; }
    }
}
