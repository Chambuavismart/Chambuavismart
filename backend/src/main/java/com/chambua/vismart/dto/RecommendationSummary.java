package com.chambua.vismart.dto;

import java.util.List;

/**
 * Unified recommendation output for a fixture.
 * Designed to be extensible for future markets and layers.
 */
public class RecommendationSummary {
    public enum OutcomeLean { HOME, DRAW, AWAY, UNKNOWN }
    public enum SampleSizeLevel { LOW, MEDIUM, HIGH, UNKNOWN }

    private Long fixtureId;
    private Long leagueId;
    private Long seasonId; // optional
    private String leagueName; // optional convenience
    private String homeTeam;
    private String awayTeam;

    // Core calls
    private OutcomeLean outcomeLean;
    private int outcomeConfidence; // 0-100 overall confidence

    private List<String> correctScoreShortlist; // e.g., ["1-0","2-0","2-1"]
    private String bttsRecommendation; // Yes/No/Lean
    private String overUnderRecommendation; // e.g., "Under 2.5 (lean)"

    // New: additional friendly presentation fields
    private Integer overUnderProbability; // probability corresponding to chosen O/U side
    private String correctScoreContext;   // short phrase explaining score shortlist context

    // Confidence breakdown (attribution)
    private String confidenceBreakdownText; // friendly text summary
    private Integer fixtureConfidenceComponent; // base from Fixture Analysis
    private Integer streakConfidenceComponent;  // contribution from Streak Insights
    private Integer adjustmentConfidence;       // net adjustments (+/-)

    // Streak sample-size traffic lights for each team
    private SampleSizeLevel homeStreakSampleLevel = SampleSizeLevel.UNKNOWN;
    private SampleSizeLevel awayStreakSampleLevel = SampleSizeLevel.UNKNOWN;
    private Integer homeStreakInstances; // nullable
    private Integer awayStreakInstances; // nullable
    private String homeStreakNote; // optional note when missing/low
    private String awayStreakNote; // optional note when missing/low

    // Divergence chip when Fixture Analysis and Streak Insight disagree
    private boolean divergenceWarning;
    private String divergenceNote; // optional explanatory text

    // Explainability bullets (top drivers)
    private List<String> rationale;

    // New: overall matches considered from Played Matches Summary (e.g., H2H window)
    private Integer analysisMatchesCount; // nullable when unknown
    // New: explicit contributions from each stage
    private java.util.List<String> fixtureAnalysisFactors;
    private java.util.List<String> streakInsightFactors;

    // Raw attachments for UI overlays (optional)
    private MatchAnalysisResponse matchAnalysis; // may be partial
    private StreakInsight homeStreak;
    private StreakInsight awayStreak;

    public RecommendationSummary() {}

    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }
    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
    public OutcomeLean getOutcomeLean() { return outcomeLean; }
    public void setOutcomeLean(OutcomeLean outcomeLean) { this.outcomeLean = outcomeLean; }
    public int getOutcomeConfidence() { return outcomeConfidence; }
    public void setOutcomeConfidence(int outcomeConfidence) { this.outcomeConfidence = outcomeConfidence; }
    public List<String> getCorrectScoreShortlist() { return correctScoreShortlist; }
    public void setCorrectScoreShortlist(List<String> correctScoreShortlist) { this.correctScoreShortlist = correctScoreShortlist; }
    public String getBttsRecommendation() { return bttsRecommendation; }
    public void setBttsRecommendation(String bttsRecommendation) { this.bttsRecommendation = bttsRecommendation; }
    public String getOverUnderRecommendation() { return overUnderRecommendation; }
    public void setOverUnderRecommendation(String overUnderRecommendation) { this.overUnderRecommendation = overUnderRecommendation; }
    public SampleSizeLevel getHomeStreakSampleLevel() { return homeStreakSampleLevel; }
    public void setHomeStreakSampleLevel(SampleSizeLevel homeStreakSampleLevel) { this.homeStreakSampleLevel = homeStreakSampleLevel; }
    public SampleSizeLevel getAwayStreakSampleLevel() { return awayStreakSampleLevel; }
    public void setAwayStreakSampleLevel(SampleSizeLevel awayStreakSampleLevel) { this.awayStreakSampleLevel = awayStreakSampleLevel; }
    public Integer getHomeStreakInstances() { return homeStreakInstances; }
    public void setHomeStreakInstances(Integer homeStreakInstances) { this.homeStreakInstances = homeStreakInstances; }
    public Integer getAwayStreakInstances() { return awayStreakInstances; }
    public void setAwayStreakInstances(Integer awayStreakInstances) { this.awayStreakInstances = awayStreakInstances; }
    public boolean isDivergenceWarning() { return divergenceWarning; }
    public void setDivergenceWarning(boolean divergenceWarning) { this.divergenceWarning = divergenceWarning; }
    public String getDivergenceNote() { return divergenceNote; }
    public void setDivergenceNote(String divergenceNote) { this.divergenceNote = divergenceNote; }
    public List<String> getRationale() { return rationale; }
    public void setRationale(List<String> rationale) { this.rationale = rationale; }
    public MatchAnalysisResponse getMatchAnalysis() { return matchAnalysis; }
    public void setMatchAnalysis(MatchAnalysisResponse matchAnalysis) { this.matchAnalysis = matchAnalysis; }
    public StreakInsight getHomeStreak() { return homeStreak; }
    public void setHomeStreak(StreakInsight homeStreak) { this.homeStreak = homeStreak; }
    public StreakInsight getAwayStreak() { return awayStreak; }
    public void setAwayStreak(StreakInsight awayStreak) { this.awayStreak = awayStreak; }

    public Integer getAnalysisMatchesCount() { return analysisMatchesCount; }
    public void setAnalysisMatchesCount(Integer analysisMatchesCount) { this.analysisMatchesCount = analysisMatchesCount; }
    public java.util.List<String> getFixtureAnalysisFactors() { return fixtureAnalysisFactors; }
    public void setFixtureAnalysisFactors(java.util.List<String> fixtureAnalysisFactors) { this.fixtureAnalysisFactors = fixtureAnalysisFactors; }
    public java.util.List<String> getStreakInsightFactors() { return streakInsightFactors; }
    public void setStreakInsightFactors(java.util.List<String> streakInsightFactors) { this.streakInsightFactors = streakInsightFactors; }

    // New fields getters/setters
    public Integer getOverUnderProbability() { return overUnderProbability; }
    public void setOverUnderProbability(Integer overUnderProbability) { this.overUnderProbability = overUnderProbability; }
    public String getCorrectScoreContext() { return correctScoreContext; }
    public void setCorrectScoreContext(String correctScoreContext) { this.correctScoreContext = correctScoreContext; }
    public String getConfidenceBreakdownText() { return confidenceBreakdownText; }
    public void setConfidenceBreakdownText(String confidenceBreakdownText) { this.confidenceBreakdownText = confidenceBreakdownText; }
    public Integer getFixtureConfidenceComponent() { return fixtureConfidenceComponent; }
    public void setFixtureConfidenceComponent(Integer fixtureConfidenceComponent) { this.fixtureConfidenceComponent = fixtureConfidenceComponent; }
    public Integer getStreakConfidenceComponent() { return streakConfidenceComponent; }
    public void setStreakConfidenceComponent(Integer streakConfidenceComponent) { this.streakConfidenceComponent = streakConfidenceComponent; }
    public Integer getAdjustmentConfidence() { return adjustmentConfidence; }
    public void setAdjustmentConfidence(Integer adjustmentConfidence) { this.adjustmentConfidence = adjustmentConfidence; }
    public String getHomeStreakNote() { return homeStreakNote; }
    public void setHomeStreakNote(String homeStreakNote) { this.homeStreakNote = homeStreakNote; }
    public String getAwayStreakNote() { return awayStreakNote; }
    public void setAwayStreakNote(String awayStreakNote) { this.awayStreakNote = awayStreakNote; }
}
