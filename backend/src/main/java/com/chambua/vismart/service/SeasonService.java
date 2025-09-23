package com.chambua.vismart.service;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Season;
import com.chambua.vismart.repository.LeagueRepository;
import com.chambua.vismart.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final LeagueRepository leagueRepository;

    public SeasonService(SeasonRepository seasonRepository, LeagueRepository leagueRepository) {
        this.seasonRepository = seasonRepository;
        this.leagueRepository = leagueRepository;
    }

    @Transactional // override readOnly to allow auto-backfill when none exist
    public List<Season> listForLeague(Long leagueId) {
        List<Season> seasons = seasonRepository.findByLeagueIdOrderByStartDateDesc(leagueId);
        if (seasons == null || seasons.isEmpty()) {
            // If no seasons exist for this league, create one from League.season so UI has a default selectable season
            leagueRepository.findById(leagueId).ifPresent(league -> {
                String name = league.getSeason();
                if (name != null && !name.isBlank()) {
                    Season s = new Season(league, name.trim(), null, null);
                    seasonRepository.save(s);
                }
            });
            // Re-read to return the newly created season if any
            seasons = seasonRepository.findByLeagueIdOrderByStartDateDesc(leagueId);
        }
        return seasons;
    }

    public Optional<Season> findById(Long seasonId) {
        return seasonRepository.findById(seasonId);
    }

    public Optional<Season> findCurrentSeason(Long leagueId) {
        LocalDate today = LocalDate.now();
        List<Season> seasons = seasonRepository.findByLeagueIdOrderByStartDateDesc(leagueId);
        // Prefer season that contains today
        Optional<Season> byDate = seasons.stream()
                .filter(s -> (s.getStartDate() == null || !today.isBefore(s.getStartDate()))
                        && (s.getEndDate() == null || !today.isAfter(s.getEndDate())))
                .max(Comparator.comparing(Season::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())));
        if (byDate.isPresent()) return byDate;
        // Otherwise fallback to most recent by startDate then id
        return seasons.stream()
                .sorted(Comparator.comparing(Season::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(Season::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst();
    }

    @Transactional
    public Season createSeason(Long leagueId, String name, LocalDate startDate, LocalDate endDate) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));
        Season s = new Season(league, name, startDate, endDate);
        return seasonRepository.save(s);
    }

    @Transactional
    public Season updateSeason(Long seasonId, String name, LocalDate startDate, LocalDate endDate, String metadata) {
        Season s = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));
        if (name != null && !name.isBlank()) s.setName(name);
        s.setStartDate(startDate);
        s.setEndDate(endDate);
        if (metadata != null) s.setMetadata(metadata);
        return seasonRepository.save(s);
    }

    @Transactional
    public void deleteSeason(Long seasonId) {
        seasonRepository.deleteById(seasonId);
    }
}
