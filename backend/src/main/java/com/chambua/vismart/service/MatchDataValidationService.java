package com.chambua.vismart.service;

import com.chambua.vismart.dto.MatchIngestItem;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchDataValidationService {

    public static class ValidationIssue {
        public enum Level { ERROR, WARNING }
        private final Level level;
        private final String message;

        public ValidationIssue(Level level, String message) {
            this.level = level;
            this.message = message;
        }
        public Level getLevel() { return level; }
        public String getMessage() { return message; }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationIssue> issues;

        public ValidationResult(boolean valid, List<ValidationIssue> issues) {
            this.valid = valid;
            this.issues = issues;
        }
        public boolean isValid() { return valid; }
        public List<ValidationIssue> getIssues() { return issues; }

        public List<String> errorsAsStrings(){
            return issues.stream().filter(i -> i.getLevel() == ValidationIssue.Level.ERROR).map(ValidationIssue::getMessage).toList();
        }
        public List<String> warningsAsStrings(){
            return issues.stream().filter(i -> i.getLevel() == ValidationIssue.Level.WARNING).map(ValidationIssue::getMessage).toList();
        }
    }

    // Backward-compatible default: match mode (goals required)
    public ValidationResult validate(List<MatchIngestItem> items) {
        return validate(items, false);
    }

    /**
     * Validate ingest items. When fixtureMode is true, goals may be null (no "Missing goals" error).
     */
    public ValidationResult validate(List<MatchIngestItem> items, boolean fixtureMode) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            issues.add(new ValidationIssue(ValidationIssue.Level.ERROR, "No matches to validate"));
            return new ValidationResult(false, issues);
        }

        // Basic field checks and fixture integrity
        for (int i = 0; i < items.size(); i++) {
            MatchIngestItem it = items.get(i);
            String ctx = context(it);
            if (it.getLeagueId() == null) issues.add(err("Missing league", ctx));
            if (it.getDate() == null) issues.add(err("Missing date", ctx));
            if (it.getRound() == null || it.getRound() <= 0) issues.add(err("Missing/invalid round", ctx));
            if (isBlank(it.getHomeTeamName()) || isBlank(it.getAwayTeamName())) issues.add(err("Missing team name(s)", ctx));
            if (!isBlank(it.getHomeTeamName()) && !isBlank(it.getAwayTeamName()) && it.getHomeTeamName().equalsIgnoreCase(it.getAwayTeamName())) {
                issues.add(err("Fixture integrity: duplicate team entries (home equals away)", ctx));
            }
            // Goals presence: required only for match uploads
            if (!fixtureMode && (it.getHomeGoals() == null || it.getAwayGoals() == null)) issues.add(err("Missing goals", ctx));
            // Non-negative validation applies when provided
            if (it.getHomeGoals() != null && it.getHomeGoals() < 0) issues.add(err("Negative home goals", ctx));
            if (it.getAwayGoals() != null && it.getAwayGoals() < 0) issues.add(err("Negative away goals", ctx));
            if (it.getHomeGoals() != null && it.getAwayGoals() != null) {
                // No-op: any int pair maps deterministically to W/D/L
            }
        }

        // Batch-level duplicate detection within same season: same date + teams
        // We use the string season field from items; service resolves to Season entity during persistence.
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            MatchIngestItem it = items.get(i);
            if (it.getDate() == null || isBlank(it.getHomeTeamName()) || isBlank(it.getAwayTeamName()) || isBlank(it.getSeason())) continue;
            String key = (it.getSeason().trim().toLowerCase()) + "|" + it.getDate() + "|" + it.getHomeTeamName().trim().toLowerCase() + "|" + it.getAwayTeamName().trim().toLowerCase();
            Integer prev = seen.putIfAbsent(key, i);
            if (prev != null) {
                String ctxA = context(items.get(prev));
                String ctxB = context(it);
                // Downgrade to WARNING: duplicates within the same batch should not block upload;
                // they will be skipped during persistence while preserving the first occurrence.
                issues.add(warn("Duplicate match in upload batch for season/date/teams: " + it.getSeason() + ", " + it.getDate() + ", " + it.getHomeTeamName() + " vs " + it.getAwayTeamName() + " | " + ctxA + " and " + ctxB));
            }
        }

        // Per-league chronological ordering by round -> date should be non-decreasing
        Map<Long, List<MatchIngestItem>> byLeague = items.stream().collect(Collectors.groupingBy(MatchIngestItem::getLeagueId));
        for (Map.Entry<Long, List<MatchIngestItem>> e : byLeague.entrySet()) {
            Long leagueId = e.getKey();
            Map<Integer, LocalDate> roundMinDate = new HashMap<>();
            for (MatchIngestItem it : e.getValue()) {
                if (it.getRound() != null && it.getDate() != null) {
                    roundMinDate.merge(it.getRound(), it.getDate(), (a,b) -> a.isBefore(b) ? a : b);
                }
            }
            List<Integer> rounds = new ArrayList<>(roundMinDate.keySet());
            Collections.sort(rounds);
            LocalDate prev = null;
            for (Integer r : rounds) {
                LocalDate d = roundMinDate.get(r);
                if (prev != null && d.isBefore(prev)) {
                    issues.add(warn("Chronological ordering: round " + r + " date (" + d + ") is before previous round date (" + prev + ") for league " + leagueId));
                }
                prev = d;
            }
        }

        // Global consistency: total GF should equal total GA across all items
        long totalGF = items.stream().mapToInt(it -> Optional.ofNullable(it.getHomeGoals()).orElse(0)).sum()
                + items.stream().mapToInt(it -> Optional.ofNullable(it.getAwayGoals()).orElse(0)).sum();
        long totalGA = totalGF; // For matches, sum of all teams' GF equals sum of all teams' GA automatically per match.
        // However, assert per-team totals only over completed matches
        Map<String, long[]> teamStats = new HashMap<>(); // name -> [GF, GA, matches]
        for (MatchIngestItem it : items) {
            if (!isBlank(it.getHomeTeamName()) && it.getHomeGoals() != null && it.getAwayGoals() != null) {
                teamStats.computeIfAbsent(it.getHomeTeamName().toLowerCase(), k -> new long[3]);
                teamStats.computeIfAbsent(it.getAwayTeamName().toLowerCase(), k -> new long[3]);
                teamStats.get(it.getHomeTeamName().toLowerCase())[0] += it.getHomeGoals();
                teamStats.get(it.getHomeTeamName().toLowerCase())[1] += it.getAwayGoals();
                teamStats.get(it.getHomeTeamName().toLowerCase())[2] += 1;
                teamStats.get(it.getAwayTeamName().toLowerCase())[0] += it.getAwayGoals();
                teamStats.get(it.getAwayTeamName().toLowerCase())[1] += it.getHomeGoals();
                teamStats.get(it.getAwayTeamName().toLowerCase())[2] += 1;
            }
        }
        long sumGFTeams = teamStats.values().stream().mapToLong(a -> a[0]).sum();
        long sumGATeams = teamStats.values().stream().mapToLong(a -> a[1]).sum();
        if (sumGFTeams != sumGATeams) {
            issues.add(err("Inconsistent goal totals across teams (GF " + sumGFTeams + " != GA " + sumGATeams + ")", "Batch"));
        }

        boolean ok = issues.stream().noneMatch(i -> i.getLevel() == ValidationIssue.Level.ERROR);
        return new ValidationResult(ok, issues);
    }

    private boolean isBlank(String s){ return s == null || s.trim().isEmpty(); }
    private ValidationIssue err(String m, String ctx){ return new ValidationIssue(ValidationIssue.Level.ERROR, formatCtx(m, ctx)); }
    private ValidationIssue warn(String m){ return new ValidationIssue(ValidationIssue.Level.WARNING, m); }
    private String context(MatchIngestItem it){
        String rd = it.getRound() == null ? "?" : String.valueOf(it.getRound());
        String h = it.getHomeTeamName() == null ? "?" : it.getHomeTeamName();
        String a = it.getAwayTeamName() == null ? "?" : it.getAwayTeamName();
        return "Round " + rd + ": " + h + " vs " + a;
    }
    private String formatCtx(String msg, String ctx){ return msg + " | " + ctx; }
}
