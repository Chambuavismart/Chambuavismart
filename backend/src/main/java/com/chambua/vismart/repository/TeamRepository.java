package com.chambua.vismart.repository;

import com.chambua.vismart.model.League;
import com.chambua.vismart.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {
    // Deletion helper: remove all teams for a league (used when deleting a league)
    long deleteByLeague_Id(Long leagueId);
    // Normalized-name based finders
    Optional<Team> findByNormalizedNameAndLeagueId(String normalizedName, Long leagueId);
    List<Team> findByNormalizedNameContainingAndLeagueId(String normalizedName, Long leagueId);

    // Legacy methods (kept temporarily for compatibility, prefer normalized methods)
    Optional<Team> findByLeagueAndNameIgnoreCase(League league, String name);
    Optional<Team> findByLeagueAndNameContainingIgnoreCase(League league, String namePart);

    // LeagueId-scoped variants for service usage without loading League entity
    @Query("select t from Team t where t.league.id = :leagueId and lower(trim(t.name)) = lower(trim(:name))")
    List<Team> findAllByLeagueIdAndNameIgnoreCase(@Param("leagueId") Long leagueId, @Param("name") String name);

    List<Team> findAllByLeagueIdAndNameContainingIgnoreCase(Long leagueId, String namePart);

    // Global search by name (any league)
    List<Team> findByNameContainingIgnoreCase(String namePart);

    // Resolve by name or alias (global; case-insensitive). Return list to avoid NonUniqueResultException when duplicates exist
    @Query("select t from Team t left join TeamAlias a on a.team = t where t.normalizedName = :normalized or lower(a.alias) = lower(:raw)")
    List<Team> findByNameOrAlias(@Param("normalized") String normalized, @Param("raw") String raw);

    // Default overloads for backward compatibility
    default List<Team> findByNameOrAlias(String name) {
        String normalized = com.chambua.vismart.util.TeamNameNormalizer.normalize(name);
        return findByNameOrAlias(normalized, name);
    }

    // Safe variant that does not assume uniqueness; used where data may contain duplicates
    @Query("select t from Team t left join TeamAlias a on a.team = t where t.normalizedName = :normalized or lower(a.alias) = lower(:raw)")
    List<Team> findAllByNameOrAliasIgnoreCase(@Param("normalized") String normalized, @Param("raw") String raw);

    // Default overload for backward compatibility
    default List<Team> findAllByNameOrAliasIgnoreCase(String name) {
        String normalized = com.chambua.vismart.util.TeamNameNormalizer.normalize(name);
        return findAllByNameOrAliasIgnoreCase(normalized, name);
    }

    // League-scoped resolver by name or alias
    @Query("select t from Team t left join TeamAlias a on a.team = t where (t.normalizedName = :normalized or lower(a.alias) = lower(:raw)) and t.league.id = :leagueId")
    Optional<Team> findByNameOrAliasInLeague(@Param("normalized") String normalized, @Param("raw") String raw, @Param("leagueId") Long leagueId);

    // Default overload for backward compatibility
    default Optional<Team> findByNameOrAliasInLeague(String name, Long leagueId) {
        String normalized = com.chambua.vismart.util.TeamNameNormalizer.normalize(name);
        return findByNameOrAliasInLeague(normalized, name, leagueId);
    }

    // Diagnostic helpers to detect normalization mismatches and trailing-space anomalies
    @Query("select count(t) from Team t where lower(trim(t.name)) = lower(trim(:raw))")
    long countByTrimmedNameIgnoreCase(@Param("raw") String raw);

    @Query("select count(t) from Team t where t.league.id = :leagueId and lower(trim(t.name)) = lower(trim(:raw))")
    long countByTrimmedNameIgnoreCaseAndLeagueId(@Param("raw") String raw, @Param("leagueId") Long leagueId);

    @Query("select count(t) from Team t where length(t.name) <> length(trim(t.name))")
    long countSpaceAnomalies();

    // Lightweight projection for search suggestions including league country and id to avoid LazyInitialization
    interface TeamSearchProjection {
        Long getId();
        String getName();
        String getCountry();
        Long getLeagueId();
        String getLeagueName();
    }

    @Query("select t.id as id, t.name as name, l.country as country, l.id as leagueId, l.name as leagueName from Team t join t.league l left join TeamAlias a on a.team = t where t.normalizedName like concat('%', :normalizedPart, '%') or lower(a.alias) like lower(concat('%', :rawPart, '%'))")
    List<TeamSearchProjection> searchByNameWithCountry(@Param("normalizedPart") String normalizedPart, @Param("rawPart") String rawPart);

    @Query("select t.id as id, t.name as name, l.country as country, l.id as leagueId, l.name as leagueName from Team t join t.league l left join TeamAlias a on a.team = t where l.id = :leagueId and (t.normalizedName like concat('%', :normalizedPart, '%') or lower(a.alias) like lower(concat('%', :rawPart, '%')))")
    List<TeamSearchProjection> searchByNameWithCountryAndLeague(@Param("normalizedPart") String normalizedPart, @Param("rawPart") String rawPart, @Param("leagueId") Long leagueId);
    
    // Fetch-join variant to ensure League is initialized with Team for DTO mapping
    @Query("select distinct t from Team t left join fetch t.league l left join TeamAlias a on a.team = t where t.normalizedName = :normalized or lower(a.alias) = lower(:raw)")
    List<Team> findByNameOrAliasWithLeague(@Param("normalized") String normalized, @Param("raw") String raw);

    // Default overload for backward compatibility
    default List<Team> findByNameOrAliasWithLeague(String name) {
        String normalized = com.chambua.vismart.util.TeamNameNormalizer.normalize(name);
        return findByNameOrAliasWithLeague(normalized, name);
    }
}
