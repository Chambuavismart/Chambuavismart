package com.chambua.vismart.dto;

import java.util.List;

public class AnalysisRequest {
    private long totalMatches;
    private Team teamA;
    private Team teamB;
    private H2H h2h;
    private Predictions predictions;

    public long getTotalMatches() { return totalMatches; }
    public void setTotalMatches(long totalMatches) { this.totalMatches = totalMatches; }
    public Team getTeamA() { return teamA; }
    public void setTeamA(Team teamA) { this.teamA = teamA; }
    public Team getTeamB() { return teamB; }
    public void setTeamB(Team teamB) { this.teamB = teamB; }
    public H2H getH2h() { return h2h; }
    public void setH2h(H2H h2h) { this.h2h = h2h; }
    public Predictions getPredictions() { return predictions; }
    public void setPredictions(Predictions predictions) { this.predictions = predictions; }

    public static class Team {
        private String name;
        private int matchesInvolved;
        private int wins;
        private int draws;
        private int losses;
        private int btts;
        private int over15;
        private int over25;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getMatchesInvolved() { return matchesInvolved; }
        public void setMatchesInvolved(int matchesInvolved) { this.matchesInvolved = matchesInvolved; }
        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }
        public int getDraws() { return draws; }
        public void setDraws(int draws) { this.draws = draws; }
        public int getLosses() { return losses; }
        public void setLosses(int losses) { this.losses = losses; }
        public int getBtts() { return btts; }
        public void setBtts(int btts) { this.btts = btts; }
        public int getOver15() { return over15; }
        public void setOver15(int over15) { this.over15 = over15; }
        public int getOver25() { return over25; }
        public void setOver25(int over25) { this.over25 = over25; }
    }

    public static class H2H {
        private String insights;
        private double goalDifferential;
        private double averageGD;
        private Last5 last5TeamA;
        private Last5 last5TeamB;
        private List<Row> history;
        private List<Row> allOrientations;
        public String getInsights() { return insights; }
        public void setInsights(String insights) { this.insights = insights; }
        public double getGoalDifferential() { return goalDifferential; }
        public void setGoalDifferential(double goalDifferential) { this.goalDifferential = goalDifferential; }
        public double getAverageGD() { return averageGD; }
        public void setAverageGD(double averageGD) { this.averageGD = averageGD; }
        public Last5 getLast5TeamA() { return last5TeamA; }
        public void setLast5TeamA(Last5 last5TeamA) { this.last5TeamA = last5TeamA; }
        public Last5 getLast5TeamB() { return last5TeamB; }
        public void setLast5TeamB(Last5 last5TeamB) { this.last5TeamB = last5TeamB; }
        public List<Row> getHistory() { return history; }
        public void setHistory(List<Row> history) { this.history = history; }
        public List<Row> getAllOrientations() { return allOrientations; }
        public void setAllOrientations(List<Row> allOrientations) { this.allOrientations = allOrientations; }
    }

    public static class Row {
        private Integer year;
        private String date;
        private String match;
        private String result;
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getMatch() { return match; }
        public void setMatch(String match) { this.match = match; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
    }

    public static class Last5 {
        private String streak;
        private int winRate;
        private int points;
        private List<String> recent;
        public String getStreak() { return streak; }
        public void setStreak(String streak) { this.streak = streak; }
        public int getWinRate() { return winRate; }
        public void setWinRate(int winRate) { this.winRate = winRate; }
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public List<String> getRecent() { return recent; }
        public void setRecent(List<String> recent) { this.recent = recent; }
    }

    public static class Predictions {
        private int win;
        private int draw;
        private int loss;
        private int btts;
        private int over15;
        private int over25;
        private int over35;
        private List<Score> correctScores;
        public int getWin() { return win; }
        public void setWin(int win) { this.win = win; }
        public int getDraw() { return draw; }
        public void setDraw(int draw) { this.draw = draw; }
        public int getLoss() { return loss; }
        public void setLoss(int loss) { this.loss = loss; }
        public int getBtts() { return btts; }
        public void setBtts(int btts) { this.btts = btts; }
        public int getOver15() { return over15; }
        public void setOver15(int over15) { this.over15 = over15; }
        public int getOver25() { return over25; }
        public void setOver25(int over25) { this.over25 = over25; }
        public int getOver35() { return over35; }
        public void setOver35(int over35) { this.over35 = over35; }
        public List<Score> getCorrectScores() { return correctScores; }
        public void setCorrectScores(List<Score> correctScores) { this.correctScores = correctScores; }
    }

    public static class Score {
        private String score;
        private double probability;
        public String getScore() { return score; }
        public void setScore(String score) { this.score = score; }
        public double getProbability() { return probability; }
        public void setProbability(double probability) { this.probability = probability; }
    }
}
