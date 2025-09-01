package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FormGuideService {

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
                "WHERE m.league_id = ?1 AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL AND m.match_date <= CURRENT_DATE";
        String baseAway =
                "SELECT m.match_date, m.round, t.id AS team_id, t.name AS team_name, m.away_goals AS gf, m.home_goals AS ga " +
                "FROM matches m JOIN teams t ON t.id = m.away_team_id " +
                "WHERE m.league_id = ?1 AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL AND m.match_date <= CURRENT_DATE";

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

        // Group rows by team and pick last N per team
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

        List<FormGuideRowDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<Row>> e : byTeam.entrySet()) {
            List<Row> list = e.getValue();
            list.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getRound, Comparator.nullsLast(Comparator.reverseOrder())));
            List<Row> lastN = list.stream().limit(limit).collect(Collectors.toList());
            if (lastN.isEmpty()) continue;

            int w=0,d=0,l=0,gf=0,ga=0,pts=0,btts=0,ov15=0,ov25=0,ov35=0;
            List<String> seq = new ArrayList<>();
            for (Row row : lastN) {
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
            double ppg = pts / (double) lastN.size();
            int bttsPct = (int) Math.round((btts * 100.0) / lastN.size());
            int over15Pct = (int) Math.round((ov15 * 100.0) / lastN.size());
            int over25Pct = (int) Math.round((ov25 * 100.0) / lastN.size());
            int over35Pct = (int) Math.round((ov35 * 100.0) / lastN.size());

            Row first = lastN.get(0);
            result.add(new FormGuideRowDTO(first.teamId, first.teamName, w, d, l, gf, ga, pts, round2(ppg), seq, bttsPct, over15Pct, over25Pct, over35Pct));
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
