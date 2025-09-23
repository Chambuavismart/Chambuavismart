package com.chambua.vismart.service;

import com.chambua.vismart.model.PdfArchive;
import com.chambua.vismart.repository.PdfArchiveRepository;
import com.chambua.vismart.dto.AnalysisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class PdfArchiveService {
    private final PdfArchiveRepository repo;
    private final ObjectMapper mapper;

    @Autowired
    public PdfArchiveService(PdfArchiveRepository repo, ObjectMapper mapper) {
        this.repo = repo; this.mapper = mapper;
    }

    public PdfArchive save(AnalysisRequest req, byte[] pdfBytes, String filename, String contentType) {
        PdfArchive e = new PdfArchive();
        String home = (req.getTeamA()!=null? req.getTeamA().getName(): "TeamA");
        String away = (req.getTeamB()!=null? req.getTeamB().getName(): "TeamB");
        e.setFilename(filename);
        e.setHomeTeam(home);
        e.setAwayTeam(away);
        e.setGeneratedAt(Instant.now());
        e.setContentType(contentType != null? contentType : "application/pdf");
        e.setSizeBytes(pdfBytes != null? pdfBytes.length : 0);
        e.setBytes(pdfBytes != null? pdfBytes : new byte[0]);
        try {
            // Snapshot the request as JSON for future reference
            String json = mapper.writeValueAsString(req);
            e.setRequestSnapshot(json);
        } catch (Exception ex) {
            e.setRequestSnapshot("{}");
        }
        return repo.save(e);
    }

    public Page<PdfArchive> list(int page, int size) {
        return repo.findAllByOrderByGeneratedAtDesc(PageRequest.of(Math.max(0,page), Math.max(1, Math.min(size, 200))));
    }

    public java.util.Optional<PdfArchive> get(Long id) { return repo.findById(id); }

    public void delete(Long id) { repo.deleteById(id); }
}
