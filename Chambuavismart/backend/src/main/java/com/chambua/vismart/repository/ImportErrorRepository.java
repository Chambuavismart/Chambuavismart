package com.chambua.vismart.repository;

import com.chambua.vismart.model.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {
    List<ImportError> findByImportRunId(Long importRunId);
}
