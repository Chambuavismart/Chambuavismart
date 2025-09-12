package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByLeagueAndNameIgnoreCase(League league, String name);
    Optional<Team> findByLeagueAndNameContainingIgnoreCase(League league, String namePart);

    // LeagueId-scoped variants for service usage without loading League entity
    List<Team> findAllByLeagueIdAndNameIgnoreCase(Long leagueId, String name);
    List<Team> findAllByLeagueIdAndNameContainingIgnoreCase(Long leagueId, String namePart);

    // Global search by name (any league)
    List<Team> findByNameContainingIgnoreCase(String namePart);

    // Resolve by name or alias (global; case-insensitive)
    @Query("select t from Team t left join TeamAlias a on a.team = t where lower(t.name) = lower(:name) or lower(a.alias) = lower(:name)")
    Optional<Team> findByNameOrAlias(@Param("name") String name);

    // Safe variant that does not assume uniqueness; used where data may contain duplicates
    @Query("select t from Team t left join TeamAlias a on a.team = t where lower(t.name) = lower(:name) or lower(a.alias) = lower(:name)")
    List<Team> findAllByNameOrAliasIgnoreCase(@Param("name") String name);

    // League-scoped resolver by name or alias
    @Query("select t from Team t left join TeamAlias a on a.team = t where (lower(t.name) = lower(:name) or lower(a.alias) = lower(:name)) and t.league.id = :leagueId")
    Optional<Team> findByNameOrAliasInLeague(@Param("name") String name, @Param("leagueId") Long leagueId);

    // Lightweight projection for search suggestions including league country to avoid LazyInitialization
    interface TeamSearchProjection {
        Long getId();
        String getName();
        String getCountry();
    }

    @Query("select t.id as id, t.name as name, l.country as country from Team t join t.league l left join TeamAlias a on a.team = t where lower(t.name) like lower(concat('%', :namePart, '%')) or lower(a.alias) like lower(concat('%', :namePart, '%'))")
    List<TeamSearchProjection> searchByNameWithCountry(@Param("namePart") String namePart);

    @Query("select t.id as id, t.name as name, l.country as country from Team t join t.league l left join TeamAlias a on a.team = t where l.id = :leagueId and (lower(t.name) like lower(concat('%', :namePart, '%')) or lower(a.alias) like lower(concat('%', :namePart, '%')))")
    List<TeamSearchProjection> searchByNameWithCountryAndLeague(@Param("namePart") String namePart, @Param("leagueId") Long leagueId);
}
