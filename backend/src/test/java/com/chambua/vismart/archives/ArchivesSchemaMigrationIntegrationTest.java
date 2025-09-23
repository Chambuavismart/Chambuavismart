package com.chambua.vismart.archives;

import com.chambua.vismart.model.ImportError;
import com.chambua.vismart.model.ImportRun;
import com.chambua.vismart.repository.ImportErrorRepository;
import com.chambua.vismart.repository.ImportRunRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ArchivesSchemaMigrationIntegrationTest {

    @Autowired private ImportRunRepository importRunRepository;
    @Autowired private ImportErrorRepository importErrorRepository;
    @Autowired private EntityManager em;

    @Test
    void repositories_can_persist_import_run_and_import_error() {
        ImportRun run = new ImportRun();
        run.setFileHash("abc123");
        run.setProvider("test");
        run.setSourceType("CSV");
        run.setFilename("sample.csv");
        run.setParams("{\"delimiter\":\",\"}");
        run = importRunRepository.save(run);
        assertThat(run.getId()).isNotNull();

        ImportError err = new ImportError();
        err.setImportRun(run);
        err.setRowNumber(5);
        err.setPayload("{\"row\":5}");
        err.setReason("Invalid date");
        err = importErrorRepository.save(err);
        assertThat(err.getId()).isNotNull();
    }

    @Test
    void matches_table_has_source_type_column() {
        // Will throw if column doesn't exist
        em.createNativeQuery("select source_type from matches where 1=0").getResultList();
    }
}
