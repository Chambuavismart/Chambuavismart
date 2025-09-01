package com.chambua.vismart.service;

import com.chambua.vismart.model.Fixture;
import com.chambua.vismart.model.FixtureStatus;
import com.chambua.vismart.repository.FixtureRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class FixtureService {

    private final FixtureRepository fixtureRepository;

    public FixtureService(FixtureRepository fixtureRepository) {
        this.fixtureRepository = fixtureRepository;
    }

    public List<Fixture> getFixturesByLeague(Long leagueId) {
        return fixtureRepository.findByLeague_IdOrderByDateTimeAsc(leagueId);
    }

    public List<Fixture> getUpcomingFixturesByLeague(Long leagueId) {
        return fixtureRepository.findByLeague_IdAndStatusInOrderByDateTimeAsc(leagueId, Arrays.asList(FixtureStatus.UPCOMING, FixtureStatus.LIVE));
    }

    public List<Fixture> saveFixtures(List<Fixture> fixtures) {
        return fixtureRepository.saveAll(fixtures);
    }
}
