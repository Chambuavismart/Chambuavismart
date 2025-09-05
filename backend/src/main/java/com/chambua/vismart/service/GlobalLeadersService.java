package com.chambua.vismart.service;

import com.chambua.vismart.dto.GlobalLeaderDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GlobalLeadersService {
    private final NamedParameterJdbcTemplate jdbc;

    // Simple in-memory cache with TTL
    private static class CacheEntry {
        final List<GlobalLeaderDto> data;
        final Instant expiresAt;
        CacheEntry(List<GlobalLeaderDto> data, Instant expiresAt) {
            this.data = data; this.expiresAt = expiresAt;
        }
    }
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis = 10 * 60 * 1000L; // 10 minutes

    public GlobalLeadersService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<GlobalLeaderDto> getLeaders(String category, int limit, int minMatches) {
        // Backward-compatible: default to overall scope and no last-N limit
        return getLeaders(category, limit, minMatches, "overall", 0);
    }

    public List<GlobalLeaderDto> getLeaders(String category, int limit, int minMatches, String scope, int lastN) {
        String cat = normalizeCategory(category);
        String sc = normalizeScope(scope);
        int ln = Math.max(0, lastN); // 0 means "all"

        String cacheKey = cat + ":" + limit + ":" + minMatches + ":" + sc + ":" + ln;
        CacheEntry ce = cache.get(cacheKey);
        if (ce != null && ce.expiresAt.isAfter(Instant.now())) {
            return ce.data;
        }

        // Conditions independent of perspective (except wins)
        String condBTTS = "(f.home_goals is not null and f.away_goals is not null and f.home_goals > 0 and f.away_goals > 0)";
        String condOver15 = "((coalesce(f.home_goals,0) + coalesce(f.away_goals,0)) >= 2)";
        String condOver25 = "((coalesce(f.home_goals,0) + coalesce(f.away_goals,0)) >= 3)";
        String condDraws = "(f.home_goals is not null and f.away_goals is not null and f.home_goals = f.away_goals)";
        String condWins = "((f.home_team_id = f.team_id and coalesce(f.home_goals,-1) > coalesce(f.away_goals,-1)) or (f.away_team_id = f.team_id and coalesce(f.away_goals,-1) > coalesce(f.home_goals,-1)))";

        String condition = switch (cat) {
            case "btts" -> condBTTS;
            case "over15" -> condOver15;
            case "over25" -> condOver25;
            case "wins" -> condWins;
            case "draws" -> condDraws;
            default -> throw new IllegalArgumentException("Unknown category: " + cat);
        };

        // Build SQL with per-team ranking to select lastN matches (MySQL 8+ window functions)
        String sql = "with \n" +
                "latest_seasons as (\n" +
                "  select l.id as league_id, max(s2.id) as latest_season_id\n" +
                "  from leagues l\n" +
                "  join seasons s2 on s2.league_id = l.id\n" +
                "  group by l.id\n" +
                "), team_matches as (\n" +
                "  select t.id as team_id, t.name as team_name, m.id as match_id, m.match_date, m.round,\n" +
                "         m.home_team_id, m.away_team_id, m.home_goals, m.away_goals,\n" +
                "         case when m.home_team_id = t.id then 1 else 0 end as is_home\n" +
                "  from teams t\n" +
                "  join matches m on (m.home_team_id = t.id or m.away_team_id = t.id)\n" +
                "  join seasons s on m.season_id = s.id\n" +
                "  join latest_seasons ls on ls.league_id = s.league_id and ls.latest_season_id = s.id\n" +
                "  where m.status = 'PLAYED'\n" +
                "), scoped as (\n" +
                "  select * from team_matches tm\n" +
                "  where (:scope = 'overall') or (:scope = 'home' and tm.is_home = 1) or (:scope = 'away' and tm.is_home = 0)\n" +
                "), ranked as (\n" +
                "  select s.*, row_number() over (partition by s.team_id order by s.match_date desc, s.round desc, s.match_id desc) as rn\n" +
                "  from scoped s\n" +
                ")\n" +
                "select \n" +
                "  t.id as team_id, t.name as team_name, null as team_slug, null as team_logo_url,\n" +
                "  count(f.match_id) as matches_played,\n" +
                "  sum(case when (" + condition + ") then 1 else 0 end) as stat_count,\n" +
                "  (sum(case when (" + condition + ") then 1.0 else 0 end) / nullif(count(f.match_id),0)) * 100.0 as stat_pct\n" +
                "from teams t\n" +
                "left join (select * from ranked where (:lastN = 0 or rn <= :lastN)) f on f.team_id = t.id\n" +
                "group by t.id, t.name\n" +
                "having count(f.match_id) >= :minMatches\n" +
                "order by stat_pct desc, stat_count desc, matches_played desc, team_name asc\n" +
                "limit :limit";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("minMatches", minMatches);
        params.addValue("limit", limit);
        params.addValue("scope", sc);
        params.addValue("lastN", ln);

        List<GlobalLeaderDto> rows = jdbc.query(sql, params, (rs, rowNum) -> new GlobalLeaderDto(
                rs.getLong("team_id"),
                rs.getString("team_name"),
                null,
                null,
                safePct(rs.getDouble("stat_pct")),
                rs.getInt("matches_played"),
                rs.getInt("stat_count"),
                cat,
                rowNum + 1
        ));

        // Fallback for databases/profiles where the advanced query returns no rows (e.g., H2 quirks)
        if (rows.isEmpty()) {
            // Build category condition using team perspective when needed
            String cond = switch (cat) {
                case "btts" -> "(coalesce(m.home_goals,0) > 0 and coalesce(m.away_goals,0) > 0)";
                case "over15" -> "((coalesce(m.home_goals,0) + coalesce(m.away_goals,0)) >= 2)";
                case "over25" -> "((coalesce(m.home_goals,0) + coalesce(m.away_goals,0)) >= 3)";
                case "draws" -> "(m.home_goals is not null and m.away_goals is not null and m.home_goals = m.away_goals)";
                case "wins" -> "((m.home_team_id = t.id and coalesce(m.home_goals,-1) > coalesce(m.away_goals,-1)) or (m.away_team_id = t.id and coalesce(m.away_goals,-1) > coalesce(m.home_goals,-1)))";
                default -> null;
            };
            if (cond != null) {
                // H2-friendly fallback: scope and per-team lastN via correlated subquery; restrict to latest seasons
                String fbSql = "select \n" +
                        "  t.id as team_id, t.name as team_name, \n" +
                        "  count(m.id) as matches_played, \n" +
                        "  sum(case when (" + cond + ") then 1 else 0 end) as stat_count, \n" +
                        "  (sum(case when (" + cond + ") then 1.0 else 0 end) / nullif(count(m.id),0)) * 100.0 as stat_pct \n" +
                        "from teams t \n" +
                        "join matches m on (m.home_team_id = t.id or m.away_team_id = t.id) \n" +
                        "join seasons s on m.season_id = s.id \n" +
                        "join (select l.id as league_id, max(s2.id) as latest_season_id from leagues l join seasons s2 on s2.league_id = l.id group by l.id) ls on ls.league_id = s.league_id and ls.latest_season_id = s.id \n" +
                        "where m.status = 'PLAYED' \n" +
                        "  and ((:scope = 'overall') or (:scope = 'home' and m.home_team_id = t.id) or (:scope = 'away' and m.away_team_id = t.id)) \n" +
                        "  and (:lastN = 0 or ( \n" +
                        "       select count(*) from matches m2 \n" +
                        "       join seasons s2 on m2.season_id = s2.id \n" +
                        "       join (select l3.id as league_id, max(s3.id) as latest_season_id from leagues l3 join seasons s3 on s3.league_id = l3.id group by l3.id) ls2 on ls2.league_id = s2.league_id and ls2.latest_season_id = s2.id \n" +
                        "       where (m2.home_team_id = t.id or m2.away_team_id = t.id) and m2.status = 'PLAYED' \n" +
                        "         and ((:scope = 'overall') or (:scope = 'home' and m2.home_team_id = t.id) or (:scope = 'away' and m2.away_team_id = t.id)) \n" +
                        "         and (m2.match_date > m.match_date or (m2.match_date = m.match_date and (coalesce(m2.round,0) > coalesce(m.round,0) or (coalesce(m2.round,0) = coalesce(m.round,0) and m2.id > m.id)))) \n" +
                        "     ) < :lastN) \n" +
                        "group by t.id, t.name \n" +
                        "having count(m.id) >= :minMatches \n" +
                        "order by stat_pct desc, stat_count desc, matches_played desc, team_name asc \n" +
                        "limit :limit";

                rows = jdbc.query(fbSql, params, (rs, rowNum) -> new GlobalLeaderDto(
                        rs.getLong("team_id"),
                        rs.getString("team_name"),
                        null,
                        null,
                        safePct(rs.getDouble("stat_pct")),
                        rs.getInt("matches_played"),
                        rs.getInt("stat_count"),
                        cat,
                        rowNum + 1
                ));
            }
        }

        cache.put(cacheKey, new CacheEntry(rows, Instant.now().plusMillis(ttlMillis)));
        return rows;
    }

    private String normalizeCategory(String category) {
        String c = Objects.toString(category, "").trim().toLowerCase();
        return switch (c) {
            case "btts", "over15", "over25", "wins", "draws" -> c;
            default -> throw new IllegalArgumentException("Unknown category: " + category);
        };
    }

    private String normalizeScope(String scope) {
        String s = Objects.toString(scope, "overall").trim().toLowerCase();
        return switch (s) {
            case "overall", "home", "away" -> s;
            default -> "overall";
        };
    }

    private double safePct(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (v < 0) return 0.0;
        if (v > 100.0) return 100.0;
        return v;
    }

    public void clearCache() {
        cache.clear();
    }
}
