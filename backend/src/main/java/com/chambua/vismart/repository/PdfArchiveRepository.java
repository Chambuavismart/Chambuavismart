package com.chambua.vismart.repository;

import com.chambua.vismart.model.PdfArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfArchiveRepository extends JpaRepository<PdfArchive, Long> {
    Page<PdfArchive> findAllByOrderByGeneratedAtDesc(Pageable pageable);
}
