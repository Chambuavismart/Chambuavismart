package com.chambua.vismart.repository;

import com.chambua.vismart.model.AdminAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminAuditRepository extends JpaRepository<AdminAudit, Long> {
    @Query("select a from AdminAudit a order by a.timestamp desc")
    Page<AdminAudit> findRecent(Pageable pageable);
}
