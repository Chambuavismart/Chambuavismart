package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chambua.vismart.repository.SeasonRepository;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.model.Match;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class FormGuideService {

    private static final Logger log = LoggerFactory.getLogger(FormGuideService.class);

    @PersistenceContext
    private EntityManager em;

    private final com.chambua.vismart.repository.TeamRepository teamRepository;

    // Additional constructor for tests or manual wiring
    public FormGuideService(EntityManager em) {
        this.em = em;
        this.teamRepository = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FormGuideService(EntityManager em, com.chambua.vismart.repository.TeamRepository teamRepository) {
        this.em = em;
        this.teamRepository = teamRepository;
    }


    public enum Scope { OVERALL, HOME, AWAY }

    public List<FormGuideRowDTO> compute(Long leagueId, int limit, Scope scope) {
        // Disallow combined seasons; API now requires a seasonId-specific call.
        throw new IllegalArgumentException("seasonId is required for form guide computation");
    }

    public List<FormGuideRowDTO> compute(Long leagueId, Long seasonId, int limit, Scope scope) {
        if (leagueId == null) throw new IllegalArgumentException("leagueId is required");
        if (seasonId == null) throw new IllegalArgumentException("seasonId is required");
        if (limit <= 0) limit = 6;
        if (scope == null) scope = Scope.OVERALL;

        // Strict filtering: do not include NULL season rows or merge by date bounds
        String baseHome =
                "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.home_goals AS gf, m.away_goals AS ga, 1 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                "FROM matches m JOIN teams t ON t.id = m.home_team_id JOIN teams opp ON opp.id = m.away_team_id " +
                "WHERE m.league_id = ?1 AND m.season_id = ?2 AND m.status = 'PLAYED'";
        String baseAway =
                "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.away_goals AS gf, m.home_goals AS ga, 0 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                "FROM matches m JOIN teams t ON t.id = m.away_team_id JOIN teams opp ON opp.id = m.home_team_id " +
                "WHERE m.league_id = ?1 AND m.season_id = ?2 AND m.status = 'PLAYED'";

        String sql;
        if (scope == Scope.HOME) {
            sql = "SELECT * FROM (" + baseHome + ") x ORDER BY team_id ASC, md DESC, CASE WHEN rnd IS NULL THEN 1 ELSE 0 END ASC, rnd DESC, match_id DESC"; // team_id asc, date desc, NULL rounds last, round desc, id desc
        } else if (scope == Scope.AWAY) {
            sql = "SELECT * FROM (" + baseAway + ") x ORDER BY team_id ASC, md DESC, CASE WHEN rnd IS NULL THEN 1 ELSE 0 END ASC, rnd DESC, match_id DESC";
        } else {
            sql = "SELECT * FROM (" + baseHome + " UNION ALL " + baseAway + ") x ORDER BY team_id ASC, md DESC, CASE WHEN rnd IS NULL THEN 1 ELSE 0 END ASC, rnd DESC, match_id DESC";
        }

        log.info("[FORM_GUIDE][SQL][main] {}", sql);
        System.out.println("[DEBUG_LOG] FORM_GUIDE SQL main: " + sql);
        var q = em.createNativeQuery(sql)
                .setParameter(1, leagueId)
                .setParameter(2, seasonId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        // Prepare maps
        Map<Long, List<Row>> byTeam = new LinkedHashMap<>();
        Map<Long, List<Row>> homeByTeam = new LinkedHashMap<>();
        Map<Long, List<Row>> awayByTeam = new LinkedHashMap<>();

        if (rows != null && !rows.isEmpty()) {
            // Group rows by team for scope-driven main metrics
            for (Object[] r : rows) {
                Object dateObj = r[0];
                java.sql.Date date = (dateObj instanceof java.sql.Date)
                        ? (java.sql.Date) dateObj
                        : (dateObj instanceof java.time.LocalDate ? java.sql.Date.valueOf((java.time.LocalDate) dateObj) : null);
                Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
                Long teamId = ((Number) r[2]).longValue();
                String teamName = (String) r[3];
                int gf = ((Number) r[4]).intValue();
                int ga = ((Number) r[5]).intValue();
                boolean isHome = ((Number) r[6]).intValue() == 1;
                String oppName = (String) r[7];
                byTeam.computeIfAbsent(teamId, k -> new ArrayList<>())
                        .add(new Row(teamId, teamName, date, round, gf, ga, isHome, oppName));
            }

            // Also build full home and away maps regardless of requested scope, for weighted splits
            log.info("[FORM_GUIDE][SQL][home] SELECT * FROM (" + baseHome + ") x ORDER BY x.team_id ASC, x.md DESC, CASE WHEN x.rnd IS NULL THEN 1 ELSE 0 END ASC, x.rnd DESC, x.match_id DESC");
            System.out.println("[DEBUG_LOG] FORM_GUIDE SQL home: SELECT * FROM (" + baseHome + ") x ORDER BY x.team_id ASC, x.md DESC, CASE WHEN x.rnd IS NULL THEN 1 ELSE 0 END ASC, x.rnd DESC, x.match_id DESC");
            var qHome = em.createNativeQuery("SELECT * FROM (" + baseHome + ") x ORDER BY x.team_id ASC, x.md DESC, CASE WHEN x.rnd IS NULL THEN 1 ELSE 0 END ASC, x.rnd DESC, x.match_id DESC")
                    .setParameter(1, leagueId)
                    .setParameter(2, seasonId);
            @SuppressWarnings("unchecked")
            List<Object[]> homeRows = qHome.getResultList();
            for (Object[] r : homeRows) {
                Object dateObj = r[0];
                java.sql.Date date = (dateObj instanceof java.sql.Date)
                        ? (java.sql.Date) dateObj
                        : (dateObj instanceof java.time.LocalDate ? java.sql.Date.valueOf((java.time.LocalDate) dateObj) : null);
                Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
                Long teamId = ((Number) r[2]).longValue();
                String teamName = (String) r[3];
                int gf = ((Number) r[4]).intValue();
                int ga = ((Number) r[5]).intValue();
                String oppName = (String) r[7];
                homeByTeam.computeIfAbsent(teamId, k -> new ArrayList<>())
                        .add(new Row(teamId, teamName, date, round, gf, ga, true, oppName));
            }
            log.info("[FORM_GUIDE][SQL][away] SELECT * FROM (" + baseAway + ") x ORDER BY x.team_id ASC, x.md DESC, CASE WHEN x.rnd IS NULL THEN 1 ELSE 0 END ASC, x.rnd DESC, x.match_id DESC");
            System.out.println("[DEBUG_LOG] FORM_GUIDE SQL away: SELECT * FROM (" + baseAway + ") x ORDER BY x.team_id ASC, x.md DESC, CASE WHEN x.rnd IS NULL THEN 1 ELSE 0 END ASC, x.rnd DESC, x.match_id DESC");
            var qAway = em.createNativeQuery("SELECT * FROM (" + baseAway + ") x ORDER BY x.team_id ASC, x.md DESC, CASE WHEN x.rnd IS NULL THEN 1 ELSE 0 END ASC, x.rnd DESC, x.match_id DESC")
                    .setParameter(1, leagueId)
                    .setParameter(2, seasonId);
            @SuppressWarnings("unchecked")
            List<Object[]> awayRows = qAway.getResultList();
            for (Object[] r : awayRows) {
                Object dateObj = r[0];
                java.sql.Date date = (dateObj instanceof java.sql.Date)
                        ? (java.sql.Date) dateObj
                        : (dateObj instanceof java.time.LocalDate ? java.sql.Date.valueOf((java.time.LocalDate) dateObj) : null);
                Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
                Long teamId = ((Number) r[2]).longValue();
                String teamName = (String) r[3];
                int gf = ((Number) r[4]).intValue();
                int ga = ((Number) r[5]).intValue();
                String oppName = (String) r[7];
                awayByTeam.computeIfAbsent(teamId, k -> new ArrayList<>())
                        .add(new Row(teamId, teamName, date, round, gf, ga, false, oppName));
            }
        } else {
            // JPQL fallback (e.g., for H2 compatibility in tests)
            log.info("[FORM_GUIDE][FALLBACK][JPQL] Building form guide via JPQL for leagueId={}, seasonId={}", leagueId, seasonId);
            List<Match> ms = em.createQuery("select m from Match m join fetch m.homeTeam join fetch m.awayTeam where m.league.id = :lid and m.season.id = :sid and (m.status = com.chambua.vismart.model.MatchStatus.PLAYED or (m.homeGoals is not null and m.awayGoals is not null)) order by m.date desc, m.round desc, m.id desc", Match.class)
                    .setParameter("lid", leagueId)
                    .setParameter("sid", seasonId)
                    .getResultList();
            for (Match m : ms) {
                java.sql.Date date = (m.getDate() != null) ? java.sql.Date.valueOf(m.getDate()) : null;
                Integer round = m.getRound();
                Long homeId = m.getHomeTeam() != null ? m.getHomeTeam().getId() : null;
                String homeName = m.getHomeTeam() != null ? m.getHomeTeam().getName() : null;
                Long awayId = m.getAwayTeam() != null ? m.getAwayTeam().getId() : null;
                String awayName = m.getAwayTeam() != null ? m.getAwayTeam().getName() : null;
                Integer hg = m.getHomeGoals() != null ? m.getHomeGoals() : 0;
                Integer ag = m.getAwayGoals() != null ? m.getAwayGoals() : 0;
                if (homeId != null) {
                    Row hr = new Row(homeId, homeName, date, round, hg, ag, true, awayName);
                    byTeam.computeIfAbsent(homeId, k -> new ArrayList<>()).add(hr);
                    homeByTeam.computeIfAbsent(homeId, k -> new ArrayList<>()).add(hr);
                }
                if (awayId != null) {
                    Row ar = new Row(awayId, awayName, date, round, ag, hg, false, homeName);
                    byTeam.computeIfAbsent(awayId, k -> new ArrayList<>()).add(ar);
                    awayByTeam.computeIfAbsent(awayId, k -> new ArrayList<>()).add(ar);
                }
            }
        }

        boolean entireLeague = (limit == Integer.MAX_VALUE);

        List<FormGuideRowDTO> result = new ArrayList<>();
        int sumTotalMp = 0;
        for (Map.Entry<Long, List<Row>> e : byTeam.entrySet()) {
            List<Row> list = e.getValue();
            list.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getRound, Comparator.nullsLast(Comparator.reverseOrder())));
            if (list.isEmpty()) continue;

            boolean usedFallback = false;
            if (scope == Scope.OVERALL && list.size() < 3) {
                try {
                    // Fallback: fetch recent played matches across all competitions for this team
                    Long teamId = e.getKey();
                    String gHome = "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.home_goals AS gf, m.away_goals AS ga, 1 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                            "FROM matches m JOIN teams t ON t.id = m.home_team_id JOIN teams opp ON opp.id = m.away_team_id " +
                            "WHERE (m.home_team_id = ?1) AND m.status = 'PLAYED' AND m.match_date IS NOT NULL AND m.match_date <= ?2";
                    String gAway = "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.away_goals AS gf, m.home_goals AS ga, 0 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                            "FROM matches m JOIN teams t ON t.id = m.away_team_id JOIN teams opp ON opp.id = m.home_team_id " +
                            "WHERE (m.away_team_id = ?1) AND m.status = 'PLAYED' AND m.match_date IS NOT NULL AND m.match_date <= ?2";
                    String gsql = "SELECT * FROM (" + gHome + " UNION ALL " + gAway + ") x ORDER BY md DESC, CASE WHEN rnd IS NULL THEN 1 ELSE 0 END ASC, rnd DESC, match_id DESC";
                    java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
                    log.info("[FormGuide][Fallback] Scoped matches <3 for teamId={}; using global recent.", teamId);
                    System.out.println("[DEBUG_LOG] FormGuide Fallback global SQL: " + gsql);
                    @SuppressWarnings("unchecked")
                    List<Object[]> gRows = em.createNativeQuery(gsql)
                            .setParameter(1, teamId)
                            .setParameter(2, java.sql.Date.valueOf(today))
                            .getResultList();
                    List<Row> gList = new ArrayList<>();
                    for (Object[] r : gRows) {
                        Object dateObj = r[0];
                        java.sql.Date date = (dateObj instanceof java.sql.Date)
                                ? (java.sql.Date) dateObj
                                : (dateObj instanceof java.time.LocalDate ? java.sql.Date.valueOf((java.time.LocalDate) dateObj) : null);
                        Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
                        Long tId = ((Number) r[2]).longValue();
                        String tName = (String) r[3];
                        int gf_ = ((Number) r[4]).intValue();
                        int ga_ = ((Number) r[5]).intValue();
                        boolean isHome = ((Number) r[6]).intValue() == 1;
                        String oppName = (String) r[7];
                        gList.add(new Row(tId, tName, date, round, gf_, ga_, isHome, oppName));
                        if (gList.size() >= Math.max(limit, 5)) { /* we only need up to limit (default 5) */ }
                    }
                    if (!gList.isEmpty()) {
                        list = gList;
                        usedFallback = true;
                    }
                } catch (Exception ex) {
                    log.warn("[FormGuide][Fallback][Error] teamId={}, err={}", e.getKey(), ex.toString());
                }
            }

            int windowSize = Math.min(list.size(), limit);

            int w=0,d=0,l=0,gf=0,ga=0,pts=0,btts=0,ov15=0,ov25=0,ov35=0;
            // Weighted accumulators
            double sumWeights = 0.0;
            double wPts = 0.0;
            double wBtts = 0.0;
            double wOv15 = 0.0;
            double wOv25 = 0.0;
            double wOv35 = 0.0;
            double wGf = 0.0;
            double wGa = 0.0;

            List<String> seq = new ArrayList<>(windowSize);
            List<String> details = new ArrayList<>(windowSize);

            for (int i = 0; i < windowSize; i++) {
                Row row = list.get(i);
                double weight = calculateWeight(i); // i=0 most recent
                sumWeights += weight;

                gf += row.gf; ga += row.ga; // keep raw totals for display
                int total = row.gf + row.ga;
                // accumulate weighted goals for and against
                wGf += row.gf * weight;
                wGa += row.ga * weight;
                if (row.gf > row.ga) { w++; pts += 3; seq.add("W"); wPts += 3 * weight; details.add("Won " + row.gf + "-" + row.ga + (row.isHome ? " vs " : " at ") + row.oppName); }
                else if (row.gf == row.ga) { d++; pts += 1; seq.add("D"); wPts += 1 * weight; details.add("Drew " + row.gf + "-" + row.ga + (row.isHome ? " vs " : " at ") + row.oppName); }
                else { l++; seq.add("L"); /* 0 pts */ details.add("Lost " + row.gf + "-" + row.ga + (row.isHome ? " vs " : " at ") + row.oppName); }

                boolean isBtts = (row.gf > 0 && row.ga > 0);
                if (isBtts) { btts++; wBtts += weight; }
                if (total >= 2) { ov15++; wOv15 += weight; }
                if (total >= 3) { ov25++; wOv25 += weight; }
                if (total >= 4) { ov35++; wOv35 += weight; }
            }

            // Cap the displayed form string to last 10 even for entire league
            if (entireLeague && seq.size() > 10) {
                seq = new ArrayList<>(seq.subList(0, 10));
                details = new ArrayList<>(details.subList(0, 10));
            }

            int mpWindow = windowSize;
            int totalMp = list.size();
            sumTotalMp += totalMp;

            // Weighted metrics (normalize by sumWeights)
            double ppg = sumWeights == 0.0 ? 0.0 : (wPts / sumWeights);
            int bttsPct = sumWeights == 0.0 ? 0 : (int) Math.round((wBtts * 100.0) / sumWeights);
            int over15Pct = sumWeights == 0.0 ? 0 : (int) Math.round((wOv15 * 100.0) / sumWeights);
            int over25Pct = sumWeights == 0.0 ? 0 : (int) Math.round((wOv25 * 100.0) / sumWeights);
            int over35Pct = sumWeights == 0.0 ? 0 : (int) Math.round((wOv35 * 100.0) / sumWeights);

            Row first = list.get(0);
            FormGuideRowDTO dto = new FormGuideRowDTO(first.teamId, first.teamName, mpWindow, totalMp, w, d, l, gf, ga, pts, round2(ppg), seq, bttsPct, over15Pct, over25Pct, over35Pct);
            dto.setLastResultsDetails(details);
            dto.setFallback(usedFallback);
            // set weighted averages for goals per match
            if (sumWeights > 0.0) {
                dto.setAvgGfWeighted(wGf / sumWeights);
                dto.setAvgGaWeighted(wGa / sumWeights);
            }

            // Compute and set weighted home/away splits using same limit
            List<Row> homeList = homeByTeam.getOrDefault(first.teamId, Collections.emptyList());
            List<Row> awayList = awayByTeam.getOrDefault(first.teamId, Collections.emptyList());
            Split homeSplit = computeWeightedSplit(homeList, limit);
            Split awaySplit = computeWeightedSplit(awayList, limit);
            dto.setWeightedHomeGoalsFor(homeSplit.avgGf);
            dto.setWeightedHomeGoalsAgainst(homeSplit.avgGa);
            dto.setWeightedAwayGoalsFor(awaySplit.avgGf);
            dto.setWeightedAwayGoalsAgainst(awaySplit.avgGa);
            dto.setWeightedHomePPG(round2(homeSplit.ppg));
            dto.setWeightedAwayPPG(round2(awaySplit.ppg));
            dto.setWeightedHomeBTTSPercent(homeSplit.bttsPct);
            dto.setWeightedAwayBTTSPercent(awaySplit.bttsPct);
            dto.setWeightedHomeOver15Percent(homeSplit.over15Pct);
            dto.setWeightedAwayOver15Percent(awaySplit.over15Pct);
            dto.setWeightedHomeOver25Percent(homeSplit.over25Pct);
            dto.setWeightedAwayOver25Percent(awaySplit.over25Pct);
            dto.setWeightedHomeOver35Percent(homeSplit.over35Pct);
            dto.setWeightedAwayOver35Percent(awaySplit.over35Pct);
            dto.setWeightedHomeMatches(homeSplit.matchCount);
            dto.setWeightedAwayMatches(awaySplit.matchCount);

            result.add(dto);
        }

        // Validation: sum of total MPs across teams should approximate total matches * factor
        try {
            String dbg = "SELECT COUNT(*) FROM matches m WHERE m.league_id = ?1 AND m.season_id = ?2 AND m.status = 'PLAYED'";
            var dq = em.createNativeQuery(dbg)
                    .setParameter(1, leagueId)
                    .setParameter(2, seasonId);
            Number completedMatches = (Number) dq.getSingleResult();
            int factor = (scope == Scope.OVERALL) ? 2 : 1;
            int expected = completedMatches.intValue() * factor;
            if (expected > 0 && Math.abs(sumTotalMp - expected) > Math.max(2, (int) Math.round(expected * 0.05))) {
                log.warn("FormGuide validation: sum(totalMp)={} differs from expected={} for leagueId={}, seasonId={}, scope=.", sumTotalMp, expected, leagueId, seasonId, scope);
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

    // Recency weight: i=0 most recent, then decays as 1/(1+i)
    private double calculateWeight(int matchIndexFromMostRecent) {
        return 1.0 / (1 + matchIndexFromMostRecent);
    }

    private static class Row {
        final Long teamId; final String teamName; final java.sql.Date date; final Integer round; final int gf; final int ga; final boolean isHome; final String oppName;
        Row(Long teamId, String teamName, java.sql.Date date, Integer round, int gf, int ga, boolean isHome, String oppName) { this.teamId = teamId; this.teamName = teamName; this.date = date; this.round = round; this.gf = gf; this.ga = ga; this.isHome = isHome; this.oppName = oppName; }
        public java.sql.Date getDate() { return date; }
        public Integer getRound() { return round; }
    }

    private static class Split {
        final double avgGf; final double avgGa; final double ppg; final int bttsPct; final int over15Pct; final int over25Pct; final int over35Pct; final int matchCount;
        Split(double avgGf, double avgGa, double ppg, int bttsPct, int over15Pct, int over25Pct, int over35Pct, int matchCount) {
            this.avgGf = avgGf; this.avgGa = avgGa; this.ppg = ppg; this.bttsPct = bttsPct; this.over15Pct = over15Pct; this.over25Pct = over25Pct; this.over35Pct = over35Pct; this.matchCount = matchCount;
        }
    }

    private Split computeWeightedSplit(List<Row> list, int limit) {
        if (list == null || list.isEmpty()) return new Split(0.0, 0.0, 0.0, 0, 0, 0, 0, 0);
        list.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getRound, Comparator.nullsLast(Comparator.reverseOrder())));
        int window = Math.min(limit, list.size());
        double sumW = 0.0, wGf = 0.0, wGa = 0.0, wPts = 0.0, wBtts = 0.0, wOv15 = 0.0, wOv25 = 0.0, wOv35 = 0.0;
        for (int i = 0; i < window; i++) {
            Row r = list.get(i);
            double w = calculateWeight(i);
            sumW += w;
            wGf += r.gf * w;
            wGa += r.ga * w;
            if (r.gf > r.ga) wPts += 3 * w; else if (r.gf == r.ga) wPts += 1 * w;
            if (r.gf > 0 && r.ga > 0) wBtts += w;
            int total = r.gf + r.ga;
            if (total >= 2) wOv15 += w;
            if (total >= 3) wOv25 += w;
            if (total >= 4) wOv35 += w;
        }
        if (sumW == 0.0) return new Split(0.0, 0.0, 0.0, 0, 0, 0, 0, window);
        double avgGf = wGf / sumW;
        double avgGa = wGa / sumW;
        double ppg = wPts / sumW;
        int bttsPct = (int) Math.round((wBtts * 100.0) / sumW);
        int over15Pct = (int) Math.round((wOv15 * 100.0) / sumW);
        int over25Pct = (int) Math.round((wOv25 * 100.0) / sumW);
        int over35Pct = (int) Math.round((wOv35 * 100.0) / sumW);
        return new Split(avgGf, avgGa, ppg, bttsPct, over15Pct, over25Pct, over35Pct, window);
    }

    // New: compute forms for specific team IDs, with season-scoped first and global fallback if sparse
    public List<FormGuideRowDTO> computeForTeams(Long leagueId, Long seasonId, int limit, List<Long> teamIds) {
        if (leagueId == null) throw new IllegalArgumentException("leagueId is required");
        if (seasonId == null) throw new IllegalArgumentException("seasonId is required");
        if (limit <= 0) limit = 5;
        if (teamIds == null || teamIds.isEmpty()) return java.util.Collections.emptyList();

        List<FormGuideRowDTO> out = new ArrayList<>(teamIds.size());
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));

        for (Long teamId : teamIds) {
            if (teamId == null) continue;
            // Season-scoped, league-scoped queries for this team
            String sHome =
                    "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.home_goals AS gf, m.away_goals AS ga, 1 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                    "FROM matches m JOIN teams t ON t.id = m.home_team_id JOIN teams opp ON opp.id = m.away_team_id " +
                    "WHERE m.league_id = ?1 AND m.season_id = ?2 AND m.status = 'PLAYED' AND m.home_team_id = ?3 AND m.match_date IS NOT NULL AND m.match_date <= ?4";
            String sAway =
                    "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.away_goals AS gf, m.home_goals AS ga, 0 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                    "FROM matches m JOIN teams t ON t.id = m.away_team_id JOIN teams opp ON opp.id = m.home_team_id " +
                    "WHERE m.league_id = ?1 AND m.season_id = ?2 AND m.status = 'PLAYED' AND m.away_team_id = ?3 AND m.match_date IS NOT NULL AND m.match_date <= ?4";
            String scoped = "SELECT * FROM (" + sHome + " UNION ALL " + sAway + ") x ORDER BY md DESC, CASE WHEN rnd IS NULL THEN 1 ELSE 0 END ASC, rnd DESC, match_id DESC";
            @SuppressWarnings("unchecked")
            List<Object[]> rs = em.createNativeQuery(scoped)
                    .setParameter(1, leagueId)
                    .setParameter(2, seasonId)
                    .setParameter(3, teamId)
                    .setParameter(4, java.sql.Date.valueOf(today))
                    .getResultList();
            List<Row> list = new ArrayList<>();
            for (Object[] r : rs) {
                Object dateObj = r[0];
                java.sql.Date date = (dateObj instanceof java.sql.Date)
                        ? (java.sql.Date) dateObj
                        : (dateObj instanceof java.time.LocalDate ? java.sql.Date.valueOf((java.time.LocalDate) dateObj) : null);
                Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
                Long tId = ((Number) r[2]).longValue();
                String tName = (String) r[3];
                int gf = ((Number) r[4]).intValue();
                int ga = ((Number) r[5]).intValue();
                boolean isHome = ((Number) r[6]).intValue() == 1;
                String oppName = (String) r[7];
                list.add(new Row(tId, tName, date, round, gf, ga, isHome, oppName));
                if (list.size() >= Math.max(limit, 5)) { /* only need up to limit for metrics */ }
            }

            boolean usedFallback = false;
            if (list.size() < 3) {
                // Global fallback for this team (all competitions)
                String gHome =
                        "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.home_goals AS gf, m.away_goals AS ga, 1 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                        "FROM matches m JOIN teams t ON t.id = m.home_team_id JOIN teams opp ON opp.id = m.away_team_id " +
                        "WHERE (m.home_team_id = ?1) AND m.status = 'PLAYED' AND m.match_date IS NOT NULL AND m.match_date <= ?2";
                String gAway =
                        "SELECT m.match_date AS md, m.round AS rnd, t.id AS team_id, t.name AS team_name, m.away_goals AS gf, m.home_goals AS ga, 0 AS is_home, opp.name AS opp_name, m.id AS match_id " +
                        "FROM matches m JOIN teams t ON t.id = m.away_team_id JOIN teams opp ON opp.id = m.home_team_id " +
                        "WHERE (m.away_team_id = ?1) AND m.status = 'PLAYED' AND m.match_date IS NOT NULL AND m.match_date <= ?2";
                String gsql = "SELECT * FROM (" + gHome + " UNION ALL " + gAway + ") x ORDER BY md DESC, CASE WHEN rnd IS NULL THEN 1 ELSE 0 END ASC, rnd DESC, match_id DESC";
                log.info("[FormGuide][Fallback] Scoped matches <3 for teamId={}; using global recent.", teamId);
                @SuppressWarnings("unchecked")
                List<Object[]> gRows = em.createNativeQuery(gsql)
                        .setParameter(1, teamId)
                        .setParameter(2, java.sql.Date.valueOf(today))
                        .getResultList();
                list.clear();
                for (Object[] r : gRows) {
                    Object dateObj = r[0];
                    java.sql.Date date = (dateObj instanceof java.sql.Date)
                            ? (java.sql.Date) dateObj
                            : (dateObj instanceof java.time.LocalDate ? java.sql.Date.valueOf((java.time.LocalDate) dateObj) : null);
                    Integer round = r[1] == null ? 0 : ((Number) r[1]).intValue();
                    Long tId = ((Number) r[2]).longValue();
                    String tName = (String) r[3];
                    int gf = ((Number) r[4]).intValue();
                    int ga = ((Number) r[5]).intValue();
                    boolean isHome = ((Number) r[6]).intValue() == 1;
                    String oppName = (String) r[7];
                    list.add(new Row(tId, tName, date, round, gf, ga, isHome, oppName));
                    if (list.size() >= Math.max(limit, 5)) { /* cap for computation */ }
                }
                usedFallback = !list.isEmpty();
            }

            if (list.isEmpty()) {
                // Nothing to report for this team
                continue;
            }

            // Compute metrics, mirroring main compute() logic for a single team
            list.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getRound, Comparator.nullsLast(Comparator.reverseOrder())));
            int windowSize = Math.min(list.size(), limit);

            int w=0,d=0,l=0,gf=0,ga=0,pts=0,btts=0,ov15=0,ov25=0,ov35=0; // raw counts
            double sumWeights = 0.0, wPts=0.0, wBtts=0.0, wOv15=0.0, wOv25=0.0, wOv35=0.0, wGf=0.0, wGa=0.0;
            List<String> seq = new ArrayList<>(windowSize);
            List<String> details = new ArrayList<>(windowSize);
            for (int i = 0; i < windowSize; i++) {
                Row row = list.get(i);
                double weight = calculateWeight(i);
                sumWeights += weight;
                gf += row.gf; ga += row.ga;
                int total = row.gf + row.ga;
                wGf += row.gf * weight; wGa += row.ga * weight;
                if (row.gf > row.ga) { w++; pts += 3; seq.add("W"); wPts += 3 * weight; details.add("Won " + row.gf + "-" + row.ga + (row.isHome ? " vs " : " at ") + row.oppName); }
                else if (row.gf == row.ga) { d++; pts += 1; seq.add("D"); wPts += 1 * weight; details.add("Drew " + row.gf + "-" + row.ga + (row.isHome ? " vs " : " at ") + row.oppName); }
                else { l++; seq.add("L"); details.add("Lost " + row.gf + "-" + row.ga + (row.isHome ? " vs " : " at ") + row.oppName); }
                boolean isBtts = (row.gf > 0 && row.ga > 0);
                if (isBtts) { btts++; wBtts += weight; }
                if (total >= 2) { ov15++; wOv15 += weight; }
                if (total >= 3) { ov25++; wOv25 += weight; }
                if (total >= 4) { ov35++; wOv35 += weight; }
            }
            int totalMp = list.size();
            double ppg = sumWeights == 0.0 ? 0.0 : (wPts / sumWeights);
            int bttsPct = sumWeights == 0.0 ? 0 : (int) Math.round((wBtts * 100.0) / sumWeights);
            int over15Pct = sumWeights == 0.0 ? 0 : (int) Math.round((wOv15 * 100.0) / sumWeights);
            int over25Pct = sumWeights == 0.0 ? 0 : (int) Math.round((wOv25 * 100.0) / sumWeights);
            int over35Pct = sumWeights == 0.0 ? 0 : (int) Math.round((wOv35 * 100.0) / sumWeights);

            Row first = list.get(0);
            FormGuideRowDTO dto = new FormGuideRowDTO(first.teamId, first.teamName, windowSize, totalMp, w, d, l, gf, ga, pts, round2(ppg), seq, bttsPct, over15Pct, over25Pct, over35Pct);
            dto.setLastResultsDetails(details);
            dto.setFallback(usedFallback);
            dto.setMatchesAvailable(list.size());
            if (usedFallback && teamRepository != null) {
                try {
                    var proj = teamRepository.findTeamProjectionById(teamId);
                    if (proj != null) {
                        dto.setSourceLeague(proj.getLeagueName());
                    }
                } catch (Exception ignored) {}
            }
            if (sumWeights > 0.0) { dto.setAvgGfWeighted(wGf / sumWeights); dto.setAvgGaWeighted(wGa / sumWeights); }

            out.add(dto);
        }
        return out;
    }
}
