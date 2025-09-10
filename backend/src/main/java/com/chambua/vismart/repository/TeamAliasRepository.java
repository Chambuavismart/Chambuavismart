package com.chambua.vismart.repository;

import com.chambua.vismart.model.TeamAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamAliasRepository extends JpaRepository<TeamAlias, Long> {
    Optional<TeamAlias> findByAlias(String alias);
    Optional<TeamAlias> findByAliasIgnoreCase(String alias);
    List<TeamAlias> findAllByAliasIgnoreCase(String alias);
}
