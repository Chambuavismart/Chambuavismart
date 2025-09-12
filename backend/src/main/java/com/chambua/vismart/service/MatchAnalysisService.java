package com.chambua.vismart.service;

import com.chambua.vismart.dto.FormGuideRowDTO;
import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.MatchAnalysisResult;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.dto.LeagueTableEntryDTO;
import com.chambua.vismart.repository.MatchAnalysisResultRepository;
import com.chambua.vismart.repository.TeamAliasRepository;
import com.chambua.vismart.repository.TeamRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.*;

@Service
public class MatchAnalysisService {

    private static final int DEFAULT_FORM_LIMIT = 6;
    private static final int DEFAULT_H2H_LIMIT = 6;

    private final MatchAnalysisResultRepository cacheRepo;
    private final ObjectMapper objectMapper;
    private final FormGuideService formGuideService;
    private final SeasonService seasonService;
    private final com.chambua.vismart.repository.MatchRepository matchRepository;
    private final LeagueTableService leagueTableService;
    private final com.chambua.vismart.repository.LeagueRepository leagueRepository; // optional for cross-season H2H family lookup
    // Optional repositories for H2H fallback via aliases/duplicates
    private final TeamRepository teamRepository; // may be null via legacy constructor
    private final TeamAliasRepository teamAliasRepository; // may be null via legacy constructor
    @Autowired(required = false)
    private H2HService h2hService;
    @Autowired(required = false)
    private com.chambua.vismart.config.FeatureFlags featureFlags;

    public MatchAnalysisService(MatchAnalysisResultRepository cacheRepo, ObjectMapper objectMapper,
                                FormGuideService formGuideService, SeasonService seasonService,
                                com.chambua.vismart.repository.MatchRepository matchRepository,
                                LeagueTableService leagueTableService) {
        this(cacheRepo, objectMapper, formGuideService, seasonService, matchRepository, leagueTableService, null, null, null);
        this.h2hService = null;
    }

    @Autowired
    public MatchAnalysisService(MatchAnalysisResultRepository cacheRepo, ObjectMapper objectMapper,
                                FormGuideService formGuideService, SeasonService seasonService,
                                com.chambua.vismart.repository.MatchRepository matchRepository,
                                LeagueTableService leagueTableService,
                                TeamRepository teamRepository,
                                TeamAliasRepository teamAliasRepository,
                                com.chambua.vismart.repository.LeagueRepository leagueRepository) {
        this.cacheRepo = cacheRepo;
        this.objectMapper = objectMapper;
        this.formGuideService = formGuideService;
        this.seasonService = seasonService;
        this.matchRepository = matchRepository;
        this.leagueTableService = leagueTableService;
        this.teamRepository = teamRepository;
        this.teamAliasRepository = teamAliasRepository;
        this.leagueRepository = leagueRepository;
    }

    public MatchAnalysisResponse analyzeDeterministic(Long leagueId, Long homeTeamId, Long awayTeamId,
                                                     String leagueName, String homeTeamName, String awayTeamName,
                                                     boolean refresh) {
        // Delegate to season-aware overload with null seasonId (backward compatibility)
        return analyzeDeterministic(leagueId, homeTeamId, awayTeamId, null, leagueName, homeTeamName, awayTeamName, refresh);
    }

    public MatchAnalysisResponse analyzeDeterministic(Long leagueId, Long homeTeamId, Long awayTeamId,
                                                     Long seasonId,
                                                     String leagueName, String homeTeamName, String awayTeamName,
                                                     boolean refresh) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MatchAnalysisService.class);
        long t0 = System.currentTimeMillis();
        logger.info("[ANALYZE][REQ] leagueId={} seasonId={} homeId={} awayId={} home='{}' away='{}' refresh={}", leagueId, seasonId, homeTeamId, awayTeamId, homeTeamName, awayTeamName, refresh);
        // If we have IDs, no explicit season context, and not refreshing, try cache first
        if (!refresh && seasonId == null && leagueId != null && homeTeamId != null && awayTeamId != null) {
            Optional<MatchAnalysisResult> cached = cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(leagueId, homeTeamId, awayTeamId);
            if (cached.isPresent()) {
                try {
                    return objectMapper.readValue(cached.get().getResultJson(), MatchAnalysisResponse.class);
                } catch (Exception ignored) { /* fall through to recompute on JSON error */ }
            }
        }

        // Deterministic seed generation (still used for other mock stats only)
        long seed;
        if (leagueId != null && homeTeamId != null && awayTeamId != null) {
            seed = computeSeed(leagueId, homeTeamId, awayTeamId);
        } else {
            // Fallback: use normalized names to remain deterministic across requests
            String key = (leagueName == null ? "" : leagueName.trim().toLowerCase()) + "|" +
                         (homeTeamName == null ? "" : homeTeamName.trim().toLowerCase()) + "|" +
                         (awayTeamName == null ? "" : awayTeamName.trim().toLowerCase());
            seed = key.hashCode();
        }
        Random random = new Random(seed);

        // Compute Win/Draw/Loss from PPG using new weighted splits (home uses weighted-home PPG, away uses weighted-away PPG)
        int home;
        int draw;
        int away;
        // Compute BTTS and Over 2.5 using weighted split values
        int btts = 50;
        int over25 = 50;
        // Track base (form-only) stats for UI visualization
        int baseHome = 33, baseDraw = 34, baseAway = 33; // sensible defaults
        int baseBtts = btts;
        int baseOver25 = over25;
        try {
            Long sid = (seasonId != null) ? seasonId : seasonService.findCurrentSeason(leagueId).map(Season::getId).orElse(null);
            if (sid != null) {
                List<FormGuideRowDTO> rows = formGuideService.compute(leagueId, sid, DEFAULT_FORM_LIMIT, FormGuideService.Scope.OVERALL);
                logger.info("[ANALYZE][FORM] leagueId={} seasonId={} rows={}", leagueId, sid, rows != null ? rows.size() : 0);
                if (seasonId != null && (rows == null || rows.isEmpty())) {
                    throw new IllegalArgumentException("No matches found for selected season");
                }
                FormGuideRowDTO homeRow = findTeamRow(rows, homeTeamId, homeTeamName);
                FormGuideRowDTO awayRow = findTeamRow(rows, awayTeamId, awayTeamName);
                logger.info("[ANALYZE][FORM] homeRowFound={} awayRowFound={} homeId={} awayId={}", homeRow != null, awayRow != null, homeTeamId, awayTeamId);

                // Determine PPG for each side with fallback to overall ppg if insufficient split matches
                double homePpg = 0.0;
                double awayPpg = 0.0;
                boolean haveHome = homeRow != null;
                boolean haveAway = awayRow != null;
                if (haveHome) {
                    int hm = homeRow.getWeightedHomeMatches();
                    homePpg = (hm >= 2 ? homeRow.getWeightedHomePPG() : homeRow.getPpg());
                }
                if (haveAway) {
                    int am = awayRow.getWeightedAwayMatches();
                    awayPpg = (am >= 2 ? awayRow.getWeightedAwayPPG() : awayRow.getPpg());
                }

                if (!haveHome || !haveAway) {
                    // If any team row missing, neutral default for W/D/L per safety
                    home = 40; draw = 20; away = 40;
                } else {
                    double total = homePpg + awayPpg;
                    if (total <= 0.0) {
                        // Equal probabilities with slight bias to draw
                        home = 33; away = 33; draw = 34;
                    } else {
                        double homeWeight = homePpg / total;
                        double awayWeight = awayPpg / total;
                        home = (int) Math.round(homeWeight * 100.0 * 0.75);
                        away = (int) Math.round(awayWeight * 100.0 * 0.75);
                        draw = 100 - (home + away);
                        if (draw < 0) draw = 0; // safety
                    }
                }

                // BTTS/Over2.5 from weighted home/away splits with fallbacks
                if (haveHome && haveAway) {
                    Integer hBtts = homeRow.getWeightedHomeBTTSPercent();
                    Integer aBtts = awayRow.getWeightedAwayBTTSPercent();
                    Integer hOv25 = homeRow.getWeightedHomeOver25Percent();
                    Integer aOv25 = awayRow.getWeightedAwayOver25Percent();

                    int hBttsEff = (homeRow.getWeightedHomeMatches() >= 2 && hBtts != null && hBtts > 0) ? hBtts : homeRow.getBttsPct();
                    int aBttsEff = (awayRow.getWeightedAwayMatches() >= 2 && aBtts != null && aBtts > 0) ? aBtts : awayRow.getBttsPct();
                    int hOv25Eff = (homeRow.getWeightedHomeMatches() >= 2 && hOv25 != null && hOv25 > 0) ? hOv25 : homeRow.getOver25Pct();
                    int aOv25Eff = (awayRow.getWeightedAwayMatches() >= 2 && aOv25 != null && aOv25 > 0) ? aOv25 : awayRow.getOver25Pct();

                    if (hBttsEff > 0 && aBttsEff > 0) {
                        btts = (int) Math.round((hBttsEff + aBttsEff) / 2.0);
                    } // else keep default 50
                    if (hOv25Eff > 0 && aOv25Eff > 0) {
                        over25 = (int) Math.round((hOv25Eff + aOv25Eff) / 2.0);
                    }
                }
                // capture base after form computation
                baseHome = home; baseDraw = draw; baseAway = away; baseBtts = btts; baseOver25 = over25;
            } else {
                // No season available: default fallback for W/D/L
                home = 40; draw = 20; away = 40;
                baseHome = home; baseDraw = draw; baseAway = away; baseBtts = btts; baseOver25 = over25;
            }
        } catch (Exception ignored) {
            // On any error, fallback to default W/D/L and keep BTTS/Over2.5 at 50%
            home = 40; draw = 20; away = 40;
            baseHome = home; baseDraw = draw; baseAway = away; baseBtts = btts; baseOver25 = over25;
        }
        
        // Integrate Head-to-Head (H2H) recency-weighted adjustments over last N matches
        int h2hWindow = 0; double h2hPpgHome = 0.0, h2hPpgAway = 0.0; int h2hBttsPct = 0, h2hOv25Pct = 0;
        java.util.List<com.chambua.vismart.model.Match> h2hUsed = null;
        try {
            // Allow H2H retrieval if we have a league and either IDs or names for both teams
            if (leagueId != null && ((homeTeamId != null && awayTeamId != null) || (homeTeamName != null && awayTeamName != null))) {
                List<com.chambua.vismart.model.Match> h2h = null;
                List<Long> leagueIds = null;
                if (leagueRepository != null) {
                    try {
                        var leagueOpt = leagueRepository.findById(leagueId);
                        if (leagueOpt.isPresent()) {
                            var league = leagueOpt.get();
                            leagueIds = leagueRepository.findIdsByNameIgnoreCaseAndCountryIgnoreCase(league.getName(), league.getCountry());
                        }
                    } catch (Exception ignored2) { /* fall back to single-league */ }
                }
                // First attempt: if both IDs are available, query by strict pair across league family or within league
                if (homeTeamId != null && awayTeamId != null) {
                    try {
                        // Prefer strict season-scoped H2H when seasonId provided
                        if (seasonId != null) {
                            h2h = matchRepository.findHeadToHeadBySeason(leagueId, seasonId, homeTeamId, awayTeamId);
                        }
                        // If none found or no season provided, try within league family or same league
                        if (h2h == null || h2h.isEmpty()) {
                            if (leagueIds != null && !leagueIds.isEmpty()) {
                                h2h = matchRepository.findHeadToHeadAcrossLeagues(leagueIds, homeTeamId, awayTeamId);
                            } else {
                                // Fallback: same-league only
                                h2h = matchRepository.findHeadToHead(leagueId, homeTeamId, awayTeamId);
                            }
                        }
                    } catch (Exception ignored2) { h2h = matchRepository.findHeadToHead(leagueId, homeTeamId, awayTeamId); }
                }

                // Fallback or primary path: resolve via name/alias-based ID sets within the league family if IDs missing or strict lookup empty
                if ((h2h == null || h2h.isEmpty()) && teamRepository != null && (homeTeamName != null || awayTeamName != null)) {
                    Set<Long> homeIds = new LinkedHashSet<>();
                    Set<Long> awayIds = new LinkedHashSet<>();
                    if (homeTeamId != null) homeIds.add(homeTeamId);
                    if (awayTeamId != null) awayIds.add(awayTeamId);
                    if (homeTeamName != null && !homeTeamName.isBlank()) {
                        String hn = homeTeamName.trim();
                        try {
                            if (leagueIds != null && !leagueIds.isEmpty()) {
                                for (Long lid : leagueIds) {
                                    try { teamRepository.findAllByLeagueIdAndNameIgnoreCase(lid, hn).forEach(t -> { if (t.getId()!=null) homeIds.add(t.getId()); }); } catch (Exception ignored3) {}
                                    try { teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(lid, hn).forEach(t -> { if (t.getId()!=null) homeIds.add(t.getId()); }); } catch (Exception ignored3) {}
                                }
                            } else {
                                teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, hn).forEach(t -> { if (t.getId()!=null) homeIds.add(t.getId()); });
                                teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(leagueId, hn).forEach(t -> { if (t.getId()!=null) homeIds.add(t.getId()); });
                            }
                        } catch (Exception ignored2) {}
                        if (teamAliasRepository != null) {
                            try { teamAliasRepository.findAllByAliasIgnoreCase(hn).forEach(a -> { if (a.getTeam()!=null && a.getTeam().getId()!=null) homeIds.add(a.getTeam().getId()); }); } catch (Exception ignored2) {}
                        }
                    }
                    if (awayTeamName != null && !awayTeamName.isBlank()) {
                        String an = awayTeamName.trim();
                        try {
                            if (leagueIds != null && !leagueIds.isEmpty()) {
                                for (Long lid : leagueIds) {
                                    try { teamRepository.findAllByLeagueIdAndNameIgnoreCase(lid, an).forEach(t -> { if (t.getId()!=null) awayIds.add(t.getId()); }); } catch (Exception ignored3) {}
                                    try { teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(lid, an).forEach(t -> { if (t.getId()!=null) awayIds.add(t.getId()); }); } catch (Exception ignored3) {}
                                }
                            } else {
                                teamRepository.findAllByLeagueIdAndNameIgnoreCase(leagueId, an).forEach(t -> { if (t.getId()!=null) awayIds.add(t.getId()); });
                                teamRepository.findAllByLeagueIdAndNameContainingIgnoreCase(leagueId, an).forEach(t -> { if (t.getId()!=null) awayIds.add(t.getId()); });
                            }
                        } catch (Exception ignored2) {}
                        if (teamAliasRepository != null) {
                            try { teamAliasRepository.findAllByAliasIgnoreCase(an).forEach(a -> { if (a.getTeam()!=null && a.getTeam().getId()!=null) awayIds.add(a.getTeam().getId()); }); } catch (Exception ignored2) {}
                        }
                    }
                    if (!homeIds.isEmpty() && !awayIds.isEmpty()) {
                        List<Long> hs = new ArrayList<>(homeIds);
                        List<Long> as = new ArrayList<>(awayIds);
                        try {
                            List<com.chambua.vismart.model.Match> h2hSets;
                            if (leagueIds != null && !leagueIds.isEmpty()) {
                                h2hSets = matchRepository.findHeadToHeadByTeamSetsAcrossLeagues(leagueIds, hs, as);
                            } else {
                                h2hSets = matchRepository.findHeadToHeadByTeamSets(leagueId, hs, as);
                            }
                            if (h2hSets != null && !h2hSets.isEmpty()) {
                                h2h = h2hSets;
                            }
                        } catch (Exception ignored2) { /* keep empty */ }
                    }
                }
                if (h2h != null && !h2h.isEmpty()) {
                    h2hUsed = h2h;
                    int window = Math.min(DEFAULT_H2H_LIMIT, h2h.size());
                    logger.info("[ANALYZE][H2H] pairsFound={} usingWindow={}", h2h.size(), window);
                    double sumW = 0.0, wHome = 0.0, wDraw = 0.0, wAway = 0.0, wBtts = 0.0, wOv25 = 0.0, wOv15 = 0.0;
                    for (int i = 0; i < window; i++) {
                        var m = h2h.get(i); // already sorted desc by date
                        double w = 1.0 / (1 + i); // recency weighting
                        sumW += w;
                        int hg, ag;
                        boolean perspectiveHomeIsHome = (m.getHomeTeam() != null && m.getHomeTeam().getId() != null && m.getHomeTeam().getId().equals(homeTeamId));
                        if (perspectiveHomeIsHome) { hg = nvl(m.getHomeGoals()); ag = nvl(m.getAwayGoals()); }
                        else { hg = nvl(m.getAwayGoals()); ag = nvl(m.getHomeGoals()); }
                        if (hg > ag) wHome += 3 * w; else if (hg == ag) wDraw += 1 * w; else wAway += 3 * w;
                        int total = hg + ag;
                        if (hg > 0 && ag > 0) wBtts += w;
                        if (total >= 2) wOv15 += w;
                        if (total >= 3) wOv25 += w;
                    }
                    if (sumW > 0.0) {
                        h2hPpgHome = (wHome + wDraw) / sumW; // 3*win + 1*draw normalized by weights -> PPG
                        h2hPpgAway = (wAway + wDraw) / sumW;
                        h2hWindow = window;
                        // Convert to probabilities from PPG as done for form (scale to 75% band, draw is remainder)
                        double totalPpg = h2hPpgHome + h2hPpgAway;
                        int h2hHomePct, h2hDrawPct, h2hAwayPct;
                        if (totalPpg <= 0.0) {
                            h2hHomePct = 33; h2hAwayPct = 33; h2hDrawPct = 34;
                        } else {
                            double hW = h2hPpgHome / totalPpg;
                            double aW = h2hPpgAway / totalPpg;
                            h2hHomePct = (int) Math.round(hW * 100.0 * 0.75);
                            h2hAwayPct = (int) Math.round(aW * 100.0 * 0.75);
                            h2hDrawPct = 100 - (h2hHomePct + h2hAwayPct);
                            if (h2hDrawPct < 0) h2hDrawPct = 0;
                        }
                        h2hBttsPct = (int) Math.round((wBtts * 100.0) / sumW);
                        h2hOv25Pct = (int) Math.round((wOv25 * 100.0) / sumW);

                        // Blend factor alpha based on number of H2H considered (up to 50% influence at N matches)
                        double alpha = Math.min(0.5, (window / (double) DEFAULT_H2H_LIMIT) * 0.5);
                        logger.info("[ANALYZE][H2H_BLEND] alpha={} form={{H:{},D:{},A:{}}} h2h={{H:{},D:{},A:{}}}", String.format("%.2f", alpha), home, draw, away, h2hHomePct, h2hDrawPct, h2hAwayPct);
                        // Blend W/D/L
                        double bHome = (1 - alpha) * home + alpha * h2hHomePct;
                        double bAway = (1 - alpha) * away + alpha * h2hAwayPct;
                        double bDraw = (1 - alpha) * draw + alpha * h2hDrawPct;
                        // Normalize to 100 with rounding safety
                        int[] triplet = normalizeTriplet((int) Math.round(bHome), (int) Math.round(bDraw), (int) Math.round(bAway));
                        home = triplet[0]; draw = triplet[1]; away = triplet[2];

                        // Blend BTTS and Over2.5
                        btts = clampPercent((int) Math.round((1 - alpha) * btts + alpha * h2hBttsPct));
                        over25 = clampPercent((int) Math.round((1 - alpha) * over25 + alpha * h2hOv25Pct));
                    }
                }
            }
        } catch (Exception ignored) { /* fallback: keep form-only values */ }
        
        // League position/strength adjustment (season-scoped)
        try {
            if (leagueId != null) {
                Long sid = (seasonId != null) ? seasonId : seasonService.findCurrentSeason(leagueId).map(Season::getId).orElse(null);
                if (sid != null) {
                    List<LeagueTableEntryDTO> table = leagueTableService.computeTableBySeasonId(leagueId, sid);
                    if (table != null && !table.isEmpty()) {
                        int n = table.size();
                        LeagueTableEntryDTO hEntry = null, aEntry = null;
                        for (LeagueTableEntryDTO e : table) {
                            if (hEntry == null && homeTeamId != null && Objects.equals(e.getTeamId(), homeTeamId)) hEntry = e;
                            if (aEntry == null && awayTeamId != null && Objects.equals(e.getTeamId(), awayTeamId)) aEntry = e;
                        }
                        // Fallback by name if IDs not provided
                        if (hEntry == null && homeTeamName != null) {
                            for (LeagueTableEntryDTO e : table) {
                                if (homeTeamName.equalsIgnoreCase(e.getTeamName())) { hEntry = e; break; }
                            }
                        }
                        if (aEntry == null && awayTeamName != null) {
                            for (LeagueTableEntryDTO e : table) {
                                if (awayTeamName.equalsIgnoreCase(e.getTeamName())) { aEntry = e; break; }
                            }
                        }
                        boolean usable = hEntry != null && aEntry != null && hEntry.getMp() > 0 && aEntry.getMp() > 0 && n > 1;
                        if (usable) {
                            double hRank = 1.0 - ((hEntry.getPosition() - 1.0) / (n - 1.0)); // 1.0 best, 0.0 worst
                            double aRank = 1.0 - ((aEntry.getPosition() - 1.0) / (n - 1.0));
                            double delta = hRank - aRank; // positive if home is stronger
                            // Max shift up to 10 points based on absolute delta
                            int maxShift = (int) Math.round(Math.min(10.0, Math.max(0.0, Math.abs(delta) * 10.0)));
                            if (maxShift > 0) {
                                if (delta > 0) {
                                    // shift from away to home, keep draw stable
                                    int take = Math.min(maxShift, away);
                                    int[] trip = normalizeTriplet(home + take, draw, away - take);
                                    home = trip[0]; draw = trip[1]; away = trip[2];
                                } else if (delta < 0) {
                                    int take = Math.min(maxShift, home);
                                    int[] trip = normalizeTriplet(home - take, draw, away + take);
                                    home = trip[0]; draw = trip[1]; away = trip[2];
                                }
                            }
                            // If teams are very close (|delta| < 0.1), slightly increase draw by up to 2 points, borrowed evenly
                            if (Math.abs(delta) < 0.1) {
                                int inc = 2;
                                int takeH = Math.min(inc / 2, home);
                                int takeA = Math.min(inc - takeH, away);
                                int[] trip = normalizeTriplet(home - takeH, draw + takeH + takeA, away - takeA);
                                home = trip[0]; draw = trip[1]; away = trip[2];
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { /* on any failure, skip league adjustment */ }
        
        // Compute xG using weighted split GF/GA with per-team fallbacks
        double xgHome = 1.5; // neutral default per spec when no valid data
        double xgAway = 1.5; // neutral default per spec when no valid data
        try {
            Long sid = (seasonId != null) ? seasonId : seasonService.findCurrentSeason(leagueId).map(Season::getId).orElse(null);
            if (sid != null) {
                List<FormGuideRowDTO> rows = formGuideService.compute(leagueId, sid, DEFAULT_FORM_LIMIT, FormGuideService.Scope.OVERALL);
                FormGuideRowDTO homeRow = findTeamRow(rows, homeTeamId, homeTeamName);
                FormGuideRowDTO awayRow = findTeamRow(rows, awayTeamId, awayTeamName);
                if (homeRow != null && awayRow != null) {
                    // home attack
                    double homeGF = (homeRow.getWeightedHomeMatches() >= 2 && homeRow.getWeightedHomeGoalsFor() > 0)
                            ? homeRow.getWeightedHomeGoalsFor()
                            : (homeRow.getAvgGfWeighted() != null && homeRow.getAvgGfWeighted() > 0 ? homeRow.getAvgGfWeighted() : 0.0);
                    // away defense
                    double awayGA = (awayRow.getWeightedAwayMatches() >= 2 && awayRow.getWeightedAwayGoalsAgainst() > 0)
                            ? awayRow.getWeightedAwayGoalsAgainst()
                            : (awayRow.getAvgGaWeighted() != null && awayRow.getAvgGaWeighted() > 0 ? awayRow.getAvgGaWeighted() : 0.0);
                    // away attack
                    double awayGF = (awayRow.getWeightedAwayMatches() >= 2 && awayRow.getWeightedAwayGoalsFor() > 0)
                            ? awayRow.getWeightedAwayGoalsFor()
                            : (awayRow.getAvgGfWeighted() != null && awayRow.getAvgGfWeighted() > 0 ? awayRow.getAvgGfWeighted() : 0.0);
                    // home defense
                    double homeGA = (homeRow.getWeightedHomeMatches() >= 2 && homeRow.getWeightedHomeGoalsAgainst() > 0)
                            ? homeRow.getWeightedHomeGoalsAgainst()
                            : (homeRow.getAvgGaWeighted() != null && homeRow.getAvgGaWeighted() > 0 ? homeRow.getAvgGaWeighted() : 0.0);

                    boolean haveHomeX = homeGF > 0 && awayGA > 0;
                    boolean haveAwayX = awayGF > 0 && homeGA > 0;
                    if (haveHomeX) xgHome = (homeGF + awayGA) / 2.0;
                    if (haveAwayX) xgAway = (awayGF + homeGA) / 2.0;
                }
            }
        } catch (Exception ignored) {
            // keep defaults
        }
        // Clamp and round to 2 decimals, and lower-bound to 0.3 for realism
        xgHome = Math.max(0.3, Math.min(3.0, xgHome));
        xgAway = Math.max(0.3, Math.min(3.0, xgAway));
        xgHome = Math.round(xgHome * 100.0) / 100.0;
        xgAway = Math.round(xgAway * 100.0) / 100.0;

        int confidence = 60 + random.nextInt(21); // 60..80
        String advice = (over25 >= 52 ? "Likely Over 2.5" : "Under 2.5 risk") +
                ", " + (btts >= 55 ? "BTTS Yes" : "BTTS Lean No");

        MatchAnalysisResponse response = new MatchAnalysisResponse(
                homeTeamName,
                awayTeamName,
                leagueName,
                new MatchAnalysisResponse.WinProbabilities(home, draw, away),
                btts,
                over25,
                new MatchAnalysisResponse.ExpectedGoals(xgHome, xgAway),
                confidence,
                advice
        );
        // attach summaries for UI (optional)
        response.setFormSummary(new MatchAnalysisResponse.FormSummary(baseHome, baseDraw, baseAway, baseBtts, baseOver25));
        if (h2hWindow > 0) {
            MatchAnalysisResponse.H2HSummary sum = new MatchAnalysisResponse.H2HSummary(h2hWindow,
                    round2(h2hPpgHome), round2(h2hPpgAway), h2hBttsPct, h2hOv25Pct);
            // Build the compact last-N list with dates and scores for UI display
            java.util.List<MatchAnalysisResponse.H2HMatchItem> items = new java.util.ArrayList<>();
            try {
                java.util.List<com.chambua.vismart.model.Match> h2hList = h2hUsed;
                int window = Math.min(h2hWindow, h2hList != null ? h2hList.size() : 0);
                for (int i = 0; i < window; i++) {
                    var m = h2hList.get(i);
                    String date = (m.getDate() != null) ? m.getDate().toString() : "";
                    String hn = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "";
                    String an = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "";
                    String score = (m.getHomeGoals() != null ? m.getHomeGoals() : 0) + "-" + (m.getAwayGoals() != null ? m.getAwayGoals() : 0);
                    items.add(new MatchAnalysisResponse.H2HMatchItem(date, hn, an, score));
                }
            } catch (Exception ignored) {}
            sum.setMatches(items);
            // Attach GD/Form/Insights only if predictive phase1 flag is ON (default true)
            boolean phase1 = (featureFlags == null) || featureFlags.isPredictiveH2HPhase1Enabled();
            if (phase1) {
                // Attach GD summary computed by H2HService (perspective: homeTeamName)
                try {
                    com.chambua.vismart.dto.GoalDifferentialSummary gd = h2hService != null ? h2hService.computeGoalDifferentialByNames(homeTeamName, awayTeamName) : null;
                    if (gd != null) sum.setGoalDifferential(gd);
                } catch (Exception ignoredGd) {}
                // Attach last-5 form for each team across all available matches
                try {
                    sum.setHomeForm(computeFormLastFive(homeTeamId, homeTeamName));
                } catch (Exception ignoredHomeForm) {}
                try {
                    sum.setAwayForm(computeFormLastFive(awayTeamId, awayTeamName));
                } catch (Exception ignoredAwayForm) {}
            }
            // Attach H2H summary whenever we have an H2H window; matches list can be empty and UI will fallback to detailed list
            try { sum.setLastN(h2hWindow); } catch (Exception ignoredConsistency) {}
            response.setH2hSummary(sum);
            // Insights text combining GD, streaks, PPG trend
            if (phase1) {
                try {
                    if (h2hService != null) {
                        String insights = h2hService.generateInsightsText(homeTeamName, awayTeamName);
                        response.setInsightsText(insights);
                    }
                } catch (Exception ignoredInsights) {}
            }
            // Also populate flat HeadToHeadMatchDto list for UI detailed section
            try {
                java.util.List<com.chambua.vismart.model.Match> h2hList2 = h2hUsed;
                java.util.List<com.chambua.vismart.dto.HeadToHeadMatchDto> raw = new java.util.ArrayList<>();
                // Provide the full cross-season H2H list for detailed display (not limited to last-N summary window)
                int total = h2hList2 != null ? h2hList2.size() : 0;
                for (int i = 0; i < total; i++) {
                    var m = h2hList2.get(i);
                    String comp = (m.getLeague() != null && m.getLeague().getName() != null) ? m.getLeague().getName() : "";
                    String hn = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "";
                    String an = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "";
                    int hg = m.getHomeGoals() != null ? m.getHomeGoals() : 0;
                    int ag = m.getAwayGoals() != null ? m.getAwayGoals() : 0;
                    raw.add(new com.chambua.vismart.dto.HeadToHeadMatchDto(m.getDate(), comp, hn, an, hg, ag));
                }
                response.setHeadToHeadMatches(raw);
            } catch (Exception ignored) {}

            // Safety: ensure H2H arrays are present when we have source matches
            try {
                java.util.List<com.chambua.vismart.model.Match> src = h2hUsed;
                int srcCount = (src != null) ? src.size() : 0;
                if (srcCount > 0) {
                    // Ensure flat list exists
                    if (response.getHeadToHeadMatches() == null || response.getHeadToHeadMatches().isEmpty()) {
                        java.util.List<com.chambua.vismart.dto.HeadToHeadMatchDto> raw2 = new java.util.ArrayList<>();
                        for (com.chambua.vismart.model.Match m : src) {
                            String comp = (m.getLeague() != null && m.getLeague().getName() != null) ? m.getLeague().getName() : "";
                            String hn = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "";
                            String an = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "";
                            int hg = m.getHomeGoals() != null ? m.getHomeGoals() : 0;
                            int ag = m.getAwayGoals() != null ? m.getAwayGoals() : 0;
                            raw2.add(new com.chambua.vismart.dto.HeadToHeadMatchDto(m.getDate(), comp, hn, an, hg, ag));
                        }
                        response.setHeadToHeadMatches(raw2);
                    }
                    // Ensure compact list exists inside summary
                    if (response.getH2hSummary() == null) {
                        MatchAnalysisResponse.H2HSummary s2 = new MatchAnalysisResponse.H2HSummary(Math.min(h2hWindow, srcCount), round2(h2hPpgHome), round2(h2hPpgAway), h2hBttsPct, h2hOv25Pct);
                        java.util.List<MatchAnalysisResponse.H2HMatchItem> items2 = new java.util.ArrayList<>();
                        int window2 = Math.min(h2hWindow, srcCount);
                        for (int i = 0; i < window2; i++) {
                            var m = src.get(i);
                            String date = (m.getDate() != null) ? m.getDate().toString() : "";
                            String hn = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "";
                            String an = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "";
                            String score = (m.getHomeGoals() != null ? m.getHomeGoals() : 0) + "-" + (m.getAwayGoals() != null ? m.getAwayGoals() : 0);
                            items2.add(new MatchAnalysisResponse.H2HMatchItem(date, hn, an, score));
                        }
                        s2.setMatches(items2);
                        response.setH2hSummary(s2);
                    } else if (response.getH2hSummary().getMatches() == null || response.getH2hSummary().getMatches().isEmpty()) {
                        java.util.List<MatchAnalysisResponse.H2HMatchItem> items3 = new java.util.ArrayList<>();
                        int window3 = Math.min(h2hWindow, srcCount);
                        for (int i = 0; i < window3; i++) {
                            var m = src.get(i);
                            String date = (m.getDate() != null) ? m.getDate().toString() : "";
                            String hn = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "";
                            String an = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "";
                            String score = (m.getHomeGoals() != null ? m.getHomeGoals() : 0) + "-" + (m.getAwayGoals() != null ? m.getAwayGoals() : 0);
                            items3.add(new MatchAnalysisResponse.H2HMatchItem(date, hn, an, score));
                        }
                        response.getH2hSummary().setMatches(items3);
                    }
                }
                int compactCount = (response.getH2hSummary() != null && response.getH2hSummary().getMatches() != null) ? response.getH2hSummary().getMatches().size() : 0;
                int flatCount = (response.getHeadToHeadMatches() != null) ? response.getHeadToHeadMatches().size() : 0;
                logger.info("[ANALYZE][H2H][OUT] h2hUsed={} compact={} flat={}", srcCount, compactCount, flatCount);
            } catch (Exception ignored2) {}
        }

        // Save to cache only for non-season-specific calls if IDs are available
        if (seasonId == null && leagueId != null && homeTeamId != null && awayTeamId != null) {
            try {
                String json = objectMapper.writeValueAsString(response);
                MatchAnalysisResult entity = cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(leagueId, homeTeamId, awayTeamId)
                        .orElse(new MatchAnalysisResult(leagueId, homeTeamId, awayTeamId, json, Instant.now()));
                entity.setResultJson(json);
                entity.setLastUpdated(Instant.now());
                cacheRepo.save(entity);
            } catch (JsonProcessingException e) {
                // ignore caching if serialization fails
            }
        }

        logger.info("[ANALYZE][RESP] W/D/L={{H:{},D:{},A:{}}} BTTS={} O2.5={} xG={{H:{},A:{}}} conf={} ms={}", response.getWinProbabilities().getHomeWin(), response.getWinProbabilities().getDraw(), response.getWinProbabilities().getAwayWin(), response.getBttsProbability(), response.getOver25Probability(), String.format("%.2f", response.getExpectedGoals().getHome()), String.format("%.2f", response.getExpectedGoals().getAway()), response.getConfidenceScore(), (System.currentTimeMillis()-t0));
        return response;
    }

    private FormGuideRowDTO findTeamRow(List<FormGuideRowDTO> rows, Long teamId, String teamName) {
        if (rows == null || rows.isEmpty()) return null;
        if (teamId != null) {
            for (FormGuideRowDTO r : rows) {
                if (Objects.equals(r.getTeamId(), teamId)) return r;
            }
        }
        if (teamName != null) {
            String tn = teamName.trim().toLowerCase();
            for (FormGuideRowDTO r : rows) {
                if (r.getTeamName() != null && r.getTeamName().trim().toLowerCase().equals(tn)) return r;
            }
        }
        return null;
    }

    private long computeSeed(Long leagueId, Long homeTeamId, Long awayTeamId) {
        // Combine IDs into a single long deterministically
        long seed = 1469598103934665603L; // FNV offset basis
        seed ^= leagueId; seed *= 1099511628211L;
        seed ^= homeTeamId; seed *= 1099511628211L;
        seed ^= awayTeamId; seed *= 1099511628211L;
        return seed;
    }

    private static int nvl(Integer v) { return v == null ? 0 : v; }

    private static int clampPercent(int v) { return Math.max(0, Math.min(100, v)); }

    // Normalizes three integers to sum to 100 by adjusting the largest absolute error
    private static int[] normalizeTriplet(int h, int d, int a) {
        int sum = h + d + a;
        if (sum == 100) return new int[]{clampPercent(h), clampPercent(d), clampPercent(a)};
        int diff = 100 - sum;
        // adjust the component with the largest value to keep ordering
        if (h >= d && h >= a) h += diff;
        else if (a >= d && a >= h) a += diff;
        else d += diff;
        // clamp again
        h = clampPercent(h); d = clampPercent(d); a = clampPercent(a);
        // in rare case clamping changed sum, fix on draw
        int fix = 100 - (h + d + a);
        d = clampPercent(d + fix);
        return new int[]{h, d, a};
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // --- Last-5 form computation ---
    private com.chambua.vismart.dto.FormSummary computeFormLastFive(Long teamId, String teamName) {
        java.util.List<com.chambua.vismart.model.Match> list = java.util.Collections.emptyList();
        try {
            if (teamId != null) {
                // Determine latest season by teamId via recent matches' season (first match's season is latest due to ordering)
                List<com.chambua.vismart.model.Match> recentAll = matchRepository.findRecentPlayedByTeamId(teamId);
                Long latestSeasonId = null;
                if (recentAll != null && !recentAll.isEmpty()) {
                    com.chambua.vismart.model.Season s = recentAll.get(0).getSeason();
                    if (s != null) latestSeasonId = s.getId();
                }
                if (latestSeasonId != null) {
                    // Filter to that season using name-based method only if necessary; prefer id path by filtering in-memory
                    list = new java.util.ArrayList<>();
                    for (com.chambua.vismart.model.Match m : recentAll) {
                        if (m.getSeason() != null && java.util.Objects.equals(m.getSeason().getId(), latestSeasonId)) {
                            list.add(m);
                        }
                    }
                } else {
                    list = recentAll;
                }
            } else if (teamName != null && !teamName.isBlank()) {
                // Use repository helpers to constrain to the most recent season that actually has matches for this team
                String q = teamName.trim();
                java.util.List<Long> seasonIds = matchRepository.findSeasonIdsForTeamNameOrdered(q);
                java.util.List<com.chambua.vismart.model.Match> chosen = java.util.Collections.emptyList();
                if (seasonIds != null && !seasonIds.isEmpty()) {
                    for (Long sid : seasonIds) {
                        if (sid == null) continue;
                        try {
                            java.util.List<com.chambua.vismart.model.Match> attempt = matchRepository.findRecentPlayedByTeamNameAndSeason(q, sid);
                            if (attempt != null && !attempt.isEmpty()) { chosen = attempt; break; }
                        } catch (Exception ignored2) {}
                    }
                }
                if (chosen == null || chosen.isEmpty()) {
                    chosen = matchRepository.findRecentPlayedByTeamName(q);
                }
                list = chosen;
            }
        } catch (Exception ignored) {}
        if (list == null || list.isEmpty()) return new com.chambua.vismart.dto.FormSummary();
        java.util.ArrayList<String> results = new java.util.ArrayList<>();
        int wins = 0, draws = 0;
        for (com.chambua.vismart.model.Match m : list) {
            if (results.size() >= 5) break;
            Integer hg = m.getHomeGoals();
            Integer ag = m.getAwayGoals();
            if (hg == null || ag == null) continue; // skip invalid
            boolean isHome = (m.getHomeTeam() != null && m.getHomeTeam().getId() != null && teamId != null && m.getHomeTeam().getId().equals(teamId))
                    || (teamId == null && m.getHomeTeam() != null && m.getHomeTeam().getName() != null && teamName != null && m.getHomeTeam().getName().equalsIgnoreCase(teamName));
            int my = isHome ? hg : ag;
            int opp = isHome ? ag : hg;
            if (my > opp) { results.add("W"); wins++; }
            else if (my == opp) { results.add("D"); draws++; }
            else { results.add("L"); }
        }
        // current streak from most recent
        String streak = "0";
        if (!results.isEmpty()) {
            String first = results.get(0);
            int count = 1;
            for (int i = 1; i < results.size(); i++) {
                if (results.get(i).equals(first)) count++; else break;
            }
            streak = count + first;
        }
        int valid = results.size();
        int winRate = (valid > 0) ? (int)Math.round((wins * 100.0) / valid) : 0;
        int points = wins * 3 + draws;
        com.chambua.vismart.dto.FormSummary fs = new com.chambua.vismart.dto.FormSummary(results, streak, winRate, points);
                // build cumulative PPG series (most recent first)
                java.util.ArrayList<Double> ppg = new java.util.ArrayList<>();
                int cum = 0; int cnt = 0;
                for (String r : results) {
                    int pts = ("W".equalsIgnoreCase(r) ? 3 : ("D".equalsIgnoreCase(r) ? 1 : 0));
                    cum += pts; cnt++;
                    double p = cnt > 0 ? ((double) cum) / cnt : 0.0;
                    ppg.add(Math.round(p * 10.0) / 10.0);
                }
                fs.setPpgSeries(ppg);
                return fs;
    }
}
