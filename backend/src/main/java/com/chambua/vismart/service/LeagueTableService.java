package com.chambua.vismart.service;

import com.chambua.vismart.dto.LeagueTableDebugDTO;
import com.chambua.vismart.dto.LeagueTableEntryDTO;
import com.chambua.vismart.repository.MatchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LeagueTableService {

    private static final Logger log = LoggerFactory.getLogger(LeagueTableService.class);

    private final MatchRepository matchRepository;

    @PersistenceContext
    private EntityManager em;

    public LeagueTableService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    /**
     * Backward-compatible method retained for existing callers (frontend, tests).
     */
    public List<LeagueTableEntryDTO> computeTable(Long leagueId) {
        return computeTable(leagueId, null);
    }

    /**
     * SQL-driven accurate standings with de-duplication and date filtering.
     * If season is provided, it is only used for logging/validation upstream; here we rely on leagueId.
     */
    public List<LeagueTableEntryDTO> computeTable(Long leagueId, String season) {
        if (leagueId == null) throw new IllegalArgumentException("leagueId is required");

        String sql =
                "SELECT t.id AS team_id, t.name AS team_name, " +
                "       SUM(s.mp) AS mp, SUM(s.w) AS w, SUM(s.d) AS d, SUM(s.l) AS l, " +
                "       SUM(s.gf) AS gf, SUM(s.ga) AS ga, SUM(s.gf) - SUM(s.ga) AS gd, SUM(s.pts) AS pts " +
                "FROM ( " +
                "  SELECT m.home_team_id AS team_id, 1 AS mp, " +
                "         CASE WHEN m.home_goals > m.away_goals THEN 1 ELSE 0 END AS w, " +
                "         CASE WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END AS d, " +
                "         CASE WHEN m.home_goals < m.away_goals THEN 1 ELSE 0 END AS l, " +
                "         m.home_goals AS gf, m.away_goals AS ga, " +
                "         CASE WHEN m.home_goals > m.away_goals THEN 3 WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END AS pts " +
                "  FROM matches m " +
                "  WHERE m.league_id = ?1 " +
                "    AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL " +
                "    AND m.match_date <= CURRENT_DATE " +
                "  UNION ALL " +
                "  SELECT m.away_team_id AS team_id, 1 AS mp, " +
                "         CASE WHEN m.away_goals > m.home_goals THEN 1 ELSE 0 END AS w, " +
                "         CASE WHEN m.away_goals = m.home_goals THEN 1 ELSE 0 END AS d, " +
                "         CASE WHEN m.away_goals < m.home_goals THEN 1 ELSE 0 END AS l, " +
                "         m.away_goals AS gf, m.home_goals AS ga, " +
                "         CASE WHEN m.away_goals > m.home_goals THEN 3 WHEN m.away_goals = m.home_goals THEN 1 ELSE 0 END AS pts " +
                "  FROM matches m " +
                "  WHERE m.league_id = ?1 " +
                "    AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL " +
                "    AND m.match_date <= CURRENT_DATE " +
                ") s " +
                "JOIN teams t ON t.id = s.team_id " +
                "GROUP BY t.id, t.name " +
                "ORDER BY pts DESC, gd DESC, gf DESC, t.name ASC";

        // Debug logging: totals and distinct fixtures
        try {
            Number totalRows = (Number) em.createNativeQuery("SELECT COUNT(*) FROM matches WHERE league_id = ?1")
                    .setParameter(1, leagueId).getSingleResult();
            Number distinctFixtures = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM (SELECT DISTINCT league_id, home_team_id, away_team_id, match_date FROM matches WHERE league_id = ?1) t")
                    .setParameter(1, leagueId).getSingleResult();
            Number teamsCount = (Number) em.createNativeQuery("SELECT COUNT(*) FROM teams WHERE league_id = ?1")
                    .setParameter(1, leagueId).getSingleResult();
            log.debug("LeagueTable compute: leagueId={}, total_rows={}, distinct_fixtures={}, teams={}", leagueId, totalRows, distinctFixtures, teamsCount);
        } catch (Exception e) {
            log.debug("LeagueTable compute: logging failed: {}", e.getMessage());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, leagueId)
                .getResultList();

        List<LeagueTableEntryDTO> result = new ArrayList<>();
        int pos = 1;
        for (Object[] r : rows) {
            Long teamId = ((Number) r[0]).longValue();
            String teamName = (String) r[1];
            int mp = ((Number) r[2]).intValue();
            int w = ((Number) r[3]).intValue();
            int d = ((Number) r[4]).intValue();
            int l = ((Number) r[5]).intValue();
            int gf = ((Number) r[6]).intValue();
            int ga = ((Number) r[7]).intValue();
            int gd = ((Number) r[8]).intValue();
            int pts = ((Number) r[9]).intValue();

            result.add(new LeagueTableEntryDTO(pos++, teamId, teamName, mp, w, d, l, gf, ga, gd, pts));
        }
        return result;
    }

    public LeagueTableDebugDTO getDiagnostics(Long leagueId) {
        if (leagueId == null) throw new IllegalArgumentException("leagueId is required");

        // A2) total rows for league
        Number totalRows = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM matches WHERE league_id = ?1")
            .setParameter(1, leagueId)
            .getSingleResult();

        // A1) distinct fixtures (by league_id, home, away, match_date, round)
        Number distinctFixtures = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM (SELECT DISTINCT league_id, home_team_id, away_team_id, match_date, round FROM matches WHERE league_id = ?1) t")
            .setParameter(1, leagueId)
            .getSingleResult();

        // A3) duplicates list (limit 100)
        @SuppressWarnings("unchecked")
        List<Object[]> dupRows = em.createNativeQuery(
                "SELECT league_id, home_team_id, away_team_id, match_date, round, COUNT(*) AS cnt " +
                "FROM matches WHERE league_id = ?1 " +
                "GROUP BY league_id, home_team_id, away_team_id, match_date, round HAVING cnt > 1 " +
                "ORDER BY cnt DESC, match_date DESC LIMIT 100")
            .setParameter(1, leagueId)
            .getResultList();
        List<LeagueTableDebugDTO.DuplicateRow> duplicates = new ArrayList<>();
        for (Object[] r : dupRows) {
            duplicates.add(new LeagueTableDebugDTO.DuplicateRow(
                    ((Number) r[0]).longValue(),
                    ((Number) r[1]).longValue(),
                    ((Number) r[2]).longValue(),
                    (java.sql.Date) r[3],
                    r[4] == null ? null : ((Number) r[4]).intValue(),
                    ((Number) r[5]).longValue()
            ));
        }

        // A4) per-team MP from union-all ground truth (completed matches only, date <= today)
        @SuppressWarnings("unchecked")
        List<Object[]> perTeam = em.createNativeQuery(
                "SELECT t.id AS team_id, t.name AS team_name, COUNT(*) AS mp FROM (" +
                        " SELECT home_team_id AS team_id FROM matches WHERE league_id = ?1 AND home_goals IS NOT NULL AND away_goals IS NOT NULL AND match_date <= CURRENT_DATE " +
                        " UNION ALL " +
                        " SELECT away_team_id AS team_id FROM matches WHERE league_id = ?1 AND home_goals IS NOT NULL AND away_goals IS NOT NULL AND match_date <= CURRENT_DATE " +
                    ") s JOIN teams t ON t.id = s.team_id GROUP BY t.id, t.name ORDER BY mp DESC, t.name ASC")
            .setParameter(1, leagueId)
            .getResultList();
        List<LeagueTableDebugDTO.PerTeamMp> perTeamMp = new ArrayList<>();
        for (Object[] r : perTeam) {
            perTeamMp.add(new LeagueTableDebugDTO.PerTeamMp(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    ((Number) r[2]).longValue()
            ));
        }

        // current API output using our canonical computeTable
        List<LeagueTableEntryDTO> apiTable = computeTable(leagueId, null);

        return new LeagueTableDebugDTO(
                totalRows.longValue(),
                distinctFixtures.longValue(),
                duplicates,
                perTeamMp,
                apiTable
        );
    }
}
