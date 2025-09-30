package com.chambua.vismart.dto;

import java.util.List;

/**
 * Summary of outcomes for a team's matches split by longest-streak matchup type
 * (same longest type vs different longest type), expressed as percentages.
 */
public class StreakSummaryResponse {
    public static class Stats {
        private int winPercent;
        private int drawPercent;
        private int lossPercent;
        private int over15Percent;
        private int over25Percent;
        private int bttsPercent;
        // New: absolute counts backing the percentages
        private int total;         // denominator used for all percentages in this bucket
        private int winCount;      // numerator for wins
        private int drawCount;     // numerator for draws
        private int lossCount;     // numerator for losses
        private int over15Count;   // numerator for over 1.5 goals
        private int over25Count;   // numerator for over 2.5 goals
        private int bttsCount;     // numerator for both teams scoring
        // New: most common win scoreline in this bucket (from the selected team perspective) and its count
        private String mostCommonWinScoreline;
        private Integer mostCommonWinCount;

        public Stats() {}

        public int getWinPercent() { return winPercent; }
        public void setWinPercent(int winPercent) { this.winPercent = winPercent; }
        public int getDrawPercent() { return drawPercent; }
        public void setDrawPercent(int drawPercent) { this.drawPercent = drawPercent; }
        public int getLossPercent() { return lossPercent; }
        public void setLossPercent(int lossPercent) { this.lossPercent = lossPercent; }
        public int getOver15Percent() { return over15Percent; }
        public void setOver15Percent(int over15Percent) { this.over15Percent = over15Percent; }
        public int getOver25Percent() { return over25Percent; }
        public void setOver25Percent(int over25Percent) { this.over25Percent = over25Percent; }
        public int getBttsPercent() { return bttsPercent; }
        public void setBttsPercent(int bttsPercent) { this.bttsPercent = bttsPercent; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getWinCount() { return winCount; }
        public void setWinCount(int winCount) { this.winCount = winCount; }
        public int getDrawCount() { return drawCount; }
        public void setDrawCount(int drawCount) { this.drawCount = drawCount; }
        public int getLossCount() { return lossCount; }
        public void setLossCount(int lossCount) { this.lossCount = lossCount; }
        public int getOver15Count() { return over15Count; }
        public void setOver15Count(int over15Count) { this.over15Count = over15Count; }
        public int getOver25Count() { return over25Count; }
        public void setOver25Count(int over25Count) { this.over25Count = over25Count; }
        public int getBttsCount() { return bttsCount; }
        public void setBttsCount(int bttsCount) { this.bttsCount = bttsCount; }
        public String getMostCommonWinScoreline() { return mostCommonWinScoreline; }
        public void setMostCommonWinScoreline(String mostCommonWinScoreline) { this.mostCommonWinScoreline = mostCommonWinScoreline; }
        public Integer getMostCommonWinCount() { return mostCommonWinCount; }
        public void setMostCommonWinCount(Integer mostCommonWinCount) { this.mostCommonWinCount = mostCommonWinCount; }
    }

    // Lightweight info about the earliest upcoming fixture for the selected team
    public static class FixtureInfo {
        private String date;     // yyyy-MM-dd
        private String homeTeam;
        private String awayTeam;
        private String league;
        private String season;

        public FixtureInfo() {}

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getHomeTeam() { return homeTeam; }
        public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
        public String getAwayTeam() { return awayTeam; }
        public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
        public String getLeague() { return league; }
        public void setLeague(String league) { this.league = league; }
        public String getSeason() { return season; }
        public void setSeason(String season) { this.season = season; }
    }

    // Compact holder for a scoreline and its frequency
    public static class ScorelineStat {
        private String scoreline; // e.g., "1-0" from selected team perspective (selected team goals first)
        private Integer count;
        public ScorelineStat() {}
        public ScorelineStat(String scoreline, Integer count) { this.scoreline = scoreline; this.count = count; }
        public String getScoreline() { return scoreline; }
        public void setScoreline(String scoreline) { this.scoreline = scoreline; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    private Stats sameStreak = new Stats();
    private Stats differentStreak = new Stats();
    // "same_streak" | "different_streak" | null
    private String fixtureContext;
    // Earliest upcoming fixture details (optional)
    private FixtureInfo upcoming;

    // New: expose each team’s overall longest-ever streak type and its date range
    private String selectedTeamType;
    private String selectedTypeFrom; // yyyy-MM-dd
    private String selectedTypeTo;   // yyyy-MM-dd
    private Integer selectedTeamCount; // count of overall longest-ever streak
    private String opponentTeamType;
    private String opponentTypeFrom; // yyyy-MM-dd
    private String opponentTypeTo;   // yyyy-MM-dd
    private Integer opponentTeamCount; // count of opponent's overall longest-ever streak

    // New: most common win scoreline by opponent longest-ever type (from selected team perspective)
    private ScorelineStat winScorelineVsW;
    private ScorelineStat winScorelineVsD;
    private ScorelineStat winScorelineVsL;
    // New: most common draw scoreline by opponent longest-ever type
    private ScorelineStat drawScorelineVsW;
    private ScorelineStat drawScorelineVsD;
    private ScorelineStat drawScorelineVsL;
    // New: most common loss scoreline by opponent longest-ever type
    private ScorelineStat lossScorelineVsW;
    private ScorelineStat lossScorelineVsD;
    private ScorelineStat lossScorelineVsL;

    // New: overall most common scorelines (all results) for same/different buckets
    private List<ScorelineStat> sameTopScorelines;
    private List<ScorelineStat> differentTopScorelines;

    // New: most common scoreline per subcategory of overall longest types (A vs B)
    private ScorelineStat scorelineWvW;
    private ScorelineStat scorelineDvD;
    private ScorelineStat scorelineLvL;
    private ScorelineStat scorelineWvD;
    private ScorelineStat scorelineDvW;
    private ScorelineStat scorelineWvL;
    private ScorelineStat scorelineLvW;
    private ScorelineStat scorelineDvL;
    private ScorelineStat scorelineLvD;

    public StreakSummaryResponse() {}

    public Stats getSameStreak() { return sameStreak; }
    public void setSameStreak(Stats sameStreak) { this.sameStreak = sameStreak; }
    public Stats getDifferentStreak() { return differentStreak; }
    public void setDifferentStreak(Stats differentStreak) { this.differentStreak = differentStreak; }
    public String getFixtureContext() { return fixtureContext; }
    public void setFixtureContext(String fixtureContext) { this.fixtureContext = fixtureContext; }
    public FixtureInfo getUpcoming() { return upcoming; }
    public void setUpcoming(FixtureInfo upcoming) { this.upcoming = upcoming; }

    public String getSelectedTeamType() { return selectedTeamType; }
    public void setSelectedTeamType(String selectedTeamType) { this.selectedTeamType = selectedTeamType; }
    public String getSelectedTypeFrom() { return selectedTypeFrom; }
    public void setSelectedTypeFrom(String selectedTypeFrom) { this.selectedTypeFrom = selectedTypeFrom; }
    public String getSelectedTypeTo() { return selectedTypeTo; }
    public void setSelectedTypeTo(String selectedTypeTo) { this.selectedTypeTo = selectedTypeTo; }
    public Integer getSelectedTeamCount() { return selectedTeamCount; }
    public void setSelectedTeamCount(Integer selectedTeamCount) { this.selectedTeamCount = selectedTeamCount; }
    public String getOpponentTeamType() { return opponentTeamType; }
    public void setOpponentTeamType(String opponentTeamType) { this.opponentTeamType = opponentTeamType; }
    public String getOpponentTypeFrom() { return opponentTypeFrom; }
    public void setOpponentTypeFrom(String opponentTypeFrom) { this.opponentTypeFrom = opponentTypeFrom; }
    public String getOpponentTypeTo() { return opponentTypeTo; }
    public void setOpponentTypeTo(String opponentTypeTo) { this.opponentTypeTo = opponentTypeTo; }
    public Integer getOpponentTeamCount() { return opponentTeamCount; }
    public void setOpponentTeamCount(Integer opponentTeamCount) { this.opponentTeamCount = opponentTeamCount; }

    public ScorelineStat getWinScorelineVsW() { return winScorelineVsW; }
    public void setWinScorelineVsW(ScorelineStat winScorelineVsW) { this.winScorelineVsW = winScorelineVsW; }
    public ScorelineStat getWinScorelineVsD() { return winScorelineVsD; }
    public void setWinScorelineVsD(ScorelineStat winScorelineVsD) { this.winScorelineVsD = winScorelineVsD; }
    public ScorelineStat getWinScorelineVsL() { return winScorelineVsL; }
    public void setWinScorelineVsL(ScorelineStat winScorelineVsL) { this.winScorelineVsL = winScorelineVsL; }

    public ScorelineStat getDrawScorelineVsW() { return drawScorelineVsW; }
    public void setDrawScorelineVsW(ScorelineStat drawScorelineVsW) { this.drawScorelineVsW = drawScorelineVsW; }
    public ScorelineStat getDrawScorelineVsD() { return drawScorelineVsD; }
    public void setDrawScorelineVsD(ScorelineStat drawScorelineVsD) { this.drawScorelineVsD = drawScorelineVsD; }
    public ScorelineStat getDrawScorelineVsL() { return drawScorelineVsL; }
    public void setDrawScorelineVsL(ScorelineStat drawScorelineVsL) { this.drawScorelineVsL = drawScorelineVsL; }

    public ScorelineStat getLossScorelineVsW() { return lossScorelineVsW; }
    public void setLossScorelineVsW(ScorelineStat lossScorelineVsW) { this.lossScorelineVsW = lossScorelineVsW; }
    public ScorelineStat getLossScorelineVsD() { return lossScorelineVsD; }
    public void setLossScorelineVsD(ScorelineStat lossScorelineVsD) { this.lossScorelineVsD = lossScorelineVsD; }
    public ScorelineStat getLossScorelineVsL() { return lossScorelineVsL; }
    public void setLossScorelineVsL(ScorelineStat lossScorelineVsL) { this.lossScorelineVsL = lossScorelineVsL; }

    public List<ScorelineStat> getSameTopScorelines() { return sameTopScorelines; }
    public void setSameTopScorelines(List<ScorelineStat> sameTopScorelines) { this.sameTopScorelines = sameTopScorelines; }
    public List<ScorelineStat> getDifferentTopScorelines() { return differentTopScorelines; }
    public void setDifferentTopScorelines(List<ScorelineStat> differentTopScorelines) { this.differentTopScorelines = differentTopScorelines; }

    public ScorelineStat getScorelineWvW() { return scorelineWvW; }
    public void setScorelineWvW(ScorelineStat scorelineWvW) { this.scorelineWvW = scorelineWvW; }
    public ScorelineStat getScorelineDvD() { return scorelineDvD; }
    public void setScorelineDvD(ScorelineStat scorelineDvD) { this.scorelineDvD = scorelineDvD; }
    public ScorelineStat getScorelineLvL() { return scorelineLvL; }
    public void setScorelineLvL(ScorelineStat scorelineLvL) { this.scorelineLvL = scorelineLvL; }
    public ScorelineStat getScorelineWvD() { return scorelineWvD; }
    public void setScorelineWvD(ScorelineStat scorelineWvD) { this.scorelineWvD = scorelineWvD; }
    public ScorelineStat getScorelineDvW() { return scorelineDvW; }
    public void setScorelineDvW(ScorelineStat scorelineDvW) { this.scorelineDvW = scorelineDvW; }
    public ScorelineStat getScorelineWvL() { return scorelineWvL; }
    public void setScorelineWvL(ScorelineStat scorelineWvL) { this.scorelineWvL = scorelineWvL; }
    public ScorelineStat getScorelineLvW() { return scorelineLvW; }
    public void setScorelineLvW(ScorelineStat scorelineLvW) { this.scorelineLvW = scorelineLvW; }
    public ScorelineStat getScorelineDvL() { return scorelineDvL; }
    public void setScorelineDvL(ScorelineStat scorelineDvL) { this.scorelineDvL = scorelineDvL; }
    public ScorelineStat getScorelineLvD() { return scorelineLvD; }
    public void setScorelineLvD(ScorelineStat scorelineLvD) { this.scorelineLvD = scorelineLvD; }
}
