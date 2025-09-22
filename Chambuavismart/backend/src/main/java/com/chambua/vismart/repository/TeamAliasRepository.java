package com.chambua.vismart.repository;

import com.chambua.vismart.model.TeamAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TeamAliasRepository extends JpaRepository<TeamAlias, Long> {
    // Deletion helper: remove all aliases for teams in a given league
    @Modifying
    @Transactional
    long deleteByTeam_League_Id(Long leagueId);
    Optional<TeamAlias> findByAlias(String alias);
    Optional<TeamAlias> findByAliasIgnoreCase(String alias);
    List<TeamAlias> findAllByAliasIgnoreCase(String alias);
}
