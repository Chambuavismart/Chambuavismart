package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class FormGuideService {

    private static final Logger log = LoggerFactory.getLogger(FormGuideService.class);

    @PersistenceContext
    private EntityManager em;

    public enum Scope { OVERALL, HOME, AWAY }

    public List<FormGuideRowDTO> compute(Long leagueId, int limit, Scope scope) {
        if (leagueId == null) throw new IllegalArgumentException("leagueId is required");
        if (limit <= 0) limit = 6;
        if (scope == null) scope = Scope.OVERALL;

        String baseHome =
                "SELECT m.match_date, m.round, t.id AS team_id, t.name AS team_name, m.home_goals AS gf, m.away_goals AS ga " +
                "FROM matches m JOIN teams t ON t.id = m.home_team_id " +
                "WHERE m.league_id = ?1 AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL";
        String baseAway =
                "SELECT m.match_date, m.round, t.id AS team_id, t.name AS team_name, m.away_goals AS gf, m.home_goals AS ga " +
                "FROM matches m JOIN teams t ON t.id = m.away_team_id " +
                "WHERE m.league_id = ?1 AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL";

        String sql;
        if (scope == Scope.HOME) {
            sql = baseHome + " ORDER BY 3, 1 DESC, 2 DESC"; // team_id, date desc, round desc
        } else if (scope == Scope.AWAY) {
            sql = baseAway + " ORDER BY 3, 1 DESC, 2 DESC";
        } else {
            sql = baseHome + " UNION ALL " + baseAway + " ORDER BY 3, 1 DESC, 2 DESC";
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, leagueId)
                .getResultList();

        // Group rows by team
        Map<Long, List<Row>> byTeam = new LinkedHashMap<>();
        for (Object[] r : rows) {
            java.sql.Date date = (java.sql.Date) r[0];
            Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
            Long teamId = ((Number) r[2]).longValue();
            String teamName = (String) r[3];
            int gf = ((Number) r[4]).intValue();
            int ga = ((Number) r[5]).intValue();
            byTeam.computeIfAbsent(teamId, k -> new ArrayList<>())
                    .add(new Row(teamId, teamName, date, round, gf, ga));
        }

        boolean entireLeague = (limit == Integer.MAX_VALUE);

        List<FormGuideRowDTO> result = new ArrayList<>();
        int sumTotalMp = 0;
        for (Map.Entry<Long, List<Row>> e : byTeam.entrySet()) {
            List<Row> list = e.getValue();
            list.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getRound, Comparator.nullsLast(Comparator.reverseOrder())));
            if (list.isEmpty()) continue;

            int windowSize = Math.min(list.size(), limit);

            int w=0,d=0,l=0,gf=0,ga=0,pts=0,btts=0,ov15=0,ov25=0,ov35=0;
            List<String> seq = new ArrayList<>(windowSize);

            for (int i = 0; i < windowSize; i++) {
                Row row = list.get(i);
                gf += row.gf; ga += row.ga;
                int total = row.gf + row.ga;
                if (row.gf > row.ga) { w++; pts += 3; seq.add("W"); }
                else if (row.gf == row.ga) { d++; pts += 1; seq.add("D"); }
                else { l++; seq.add("L"); }
                if (row.gf > 0 && row.ga > 0) btts++;
                if (total >= 2) ov15++;
                if (total >= 3) ov25++;
                if (total >= 4) ov35++;
            }

            // Cap the displayed form string to last 10 even for entire league
            if (entireLeague && seq.size() > 10) {
                seq = new ArrayList<>(seq.subList(0, 10));
            }

            int mpWindow = windowSize;
            int totalMp = list.size();
            sumTotalMp += totalMp;

            double ppg = mpWindow == 0 ? 0.0 : (pts / (double) mpWindow);
            int bttsPct = mpWindow == 0 ? 0 : (int) Math.round((btts * 100.0) / mpWindow);
            int over15Pct = mpWindow == 0 ? 0 : (int) Math.round((ov15 * 100.0) / mpWindow);
            int over25Pct = mpWindow == 0 ? 0 : (int) Math.round((ov25 * 100.0) / mpWindow);
            int over35Pct = mpWindow == 0 ? 0 : (int) Math.round((ov35 * 100.0) / mpWindow);

            Row first = list.get(0);
            result.add(new FormGuideRowDTO(first.teamId, first.teamName, mpWindow, totalMp, w, d, l, gf, ga, pts, round2(ppg), seq, bttsPct, over15Pct, over25Pct, over35Pct));
        }

        // Validation: sum of total MPs across teams should approximate total matches * factor
        try {
            Number completedMatches = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM matches m WHERE m.league_id = ?1 AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL")
                    .setParameter(1, leagueId)
                    .getSingleResult();
            int factor = (scope == Scope.OVERALL) ? 2 : 1;
            int expected = completedMatches.intValue() * factor;
            if (expected > 0 && Math.abs(sumTotalMp - expected) > Math.max(2, (int) Math.round(expected * 0.05))) {
                log.warn("FormGuide validation: sum(totalMp)={} differs from expected={} for leagueId={}, scope={}.", sumTotalMp, expected, leagueId, scope);
            }
        } catch (Exception ex) {
            log.debug("FormGuide validation skipped due to error: {}", ex.toString());
        }

        // sort by points desc, gd desc, gf desc, then name
        result.sort(Comparator
                .comparing(FormGuideRowDTO::getPts).reversed()
                .thenComparing(r -> r.getGd(), Comparator.reverseOrder())
                .thenComparing(FormGuideRowDTO::getGf, Comparator.reverseOrder())
                .thenComparing(FormGuideRowDTO::getTeamName));
        return result;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static class Row {
        final Long teamId; final String teamName; final java.sql.Date date; final Integer round; final int gf; final int ga;
        Row(Long teamId, String teamName, java.sql.Date date, Integer round, int gf, int ga) { this.teamId = teamId; this.teamName = teamName; this.date = date; this.round = round; this.gf = gf; this.ga = ga; }
        public java.sql.Date getDate() { return date; }
        public Integer getRound() { return round; }
    }
}
