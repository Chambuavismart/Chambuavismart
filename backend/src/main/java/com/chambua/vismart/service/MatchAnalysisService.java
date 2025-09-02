package com.chambua.vismart.service;

import com.chambua.vismart.dto.MatchAnalysisResponse;
import com.chambua.vismart.model.MatchAnalysisResult;
import com.chambua.vismart.repository.MatchAnalysisResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Service
public class MatchAnalysisService {

    private final MatchAnalysisResultRepository cacheRepo;
    private final ObjectMapper objectMapper;

    public MatchAnalysisService(MatchAnalysisResultRepository cacheRepo, ObjectMapper objectMapper) {
        this.cacheRepo = cacheRepo;
        this.objectMapper = objectMapper;
    }

    public MatchAnalysisResponse analyzeDeterministic(Long leagueId, Long homeTeamId, Long awayTeamId,
                                                      String leagueName, String homeTeamName, String awayTeamName,
                                                      boolean refresh) {
        // If we have IDs and not refreshing, try cache first
        if (!refresh && leagueId != null && homeTeamId != null && awayTeamId != null) {
            Optional<MatchAnalysisResult> cached = cacheRepo.findByLeagueIdAndHomeTeamIdAndAwayTeamId(leagueId, homeTeamId, awayTeamId);
            if (cached.isPresent()) {
                try {
                    return objectMapper.readValue(cached.get().getResultJson(), MatchAnalysisResponse.class);
                } catch (Exception ignored) { /* fall through to recompute on JSON error */ }
            }
        }

        // Deterministic seed generation
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

        // Mocked but deterministic logic: generate plausible numbers using seeded RNG
        int home = 40 + random.nextInt(21) - 10; // 30..50
        int draw = 20 + random.nextInt(11) - 5;  // 15..25
        int away = 100 - (home + draw);
        if (away < 10) { away = 10; home = Math.max(20, 100 - draw - away); }

        int btts = 50 + random.nextInt(21) - 10; // 40..60
        int over25 = 50 + random.nextInt(21) - 10; // 40..60

        double xgHome = Math.round((1.2 + random.nextDouble() * 0.8) * 10.0) / 10.0; // 1.2..2.0
        double xgAway = Math.round((1.0 + random.nextDouble() * 0.7) * 10.0) / 10.0; // 1.0..1.7

        int confidence = 60 + random.nextInt(21); // 60..80
        String advice = (over25 >= 52 ? "Likely Over 2.5" : "Under 2.5 risk") +
                ", " + (btts >= 55 ? "BTTS Yes" : "BTTS Lean No");

        MatchAnalysisResponse response = new MatchAnalysisResponse(
                homeTeamName,
                awayTeamName,
                leagueName,
                new MatchAnalysisResponse.WinProbabilities(home, draw, Math.max(0, 100 - home - draw)),
                btts,
                over25,
                new MatchAnalysisResponse.ExpectedGoals(xgHome, xgAway),
                confidence,
                advice
        );

        // Save to cache if IDs are available
        if (leagueId != null && homeTeamId != null && awayTeamId != null) {
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

        return response;
    }

    private long computeSeed(Long leagueId, Long homeTeamId, Long awayTeamId) {
        // Combine IDs into a single long deterministically
        long seed = 1469598103934665603L; // FNV offset basis
        seed ^= leagueId; seed *= 1099511628211L;
        seed ^= homeTeamId; seed *= 1099511628211L;
        seed ^= awayTeamId; seed *= 1099511628211L;
        return seed;
    }
}
