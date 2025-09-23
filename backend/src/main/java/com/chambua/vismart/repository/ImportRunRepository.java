package com.chambua.vismart.repository;

import com.chambua.vismart.model.ImportRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {
    Page<ImportRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
