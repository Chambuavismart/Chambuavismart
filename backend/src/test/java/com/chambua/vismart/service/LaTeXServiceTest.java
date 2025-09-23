package com.chambua.vismart.service;

import com.chambua.vismart.dto.AnalysisRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class LaTeXServiceTest {

    @Test
    public void generatesRichFallbackPdfWithWatermarkWhenLatexUnavailable() throws Exception {
        // Force-disable latexmk so fallback is used
        System.setProperty("DISABLE_LATEXMK", "true");
        LaTeXService svc = new LaTeXService();

        AnalysisRequest req = new AnalysisRequest();
        req.setTotalMatches(3547);

        AnalysisRequest.Team a = new AnalysisRequest.Team();
        a.setName("Darmstadt");
        a.setMatchesInvolved(140);
        a.setWins(66); a.setDraws(30); a.setLosses(44);
        a.setBtts(84); a.setOver15(117); a.setOver25(89);
        req.setTeamA(a);

        AnalysisRequest.Team b = new AnalysisRequest.Team();
        b.setName("Braunschweig");
        b.setMatchesInvolved(140);
        b.setWins(37); b.setDraws(35); b.setLosses(68);
        b.setBtts(72); b.setOver15(111); b.setOver25(76);
        req.setTeamB(b);

        AnalysisRequest.H2H h = new AnalysisRequest.H2H();
        h.setInsights("Sample insights text");
        h.setGoalDifferential(5);
        h.setAverageGD(1.67);
        req.setH2h(h);

        AnalysisRequest.Predictions p = new AnalysisRequest.Predictions();
        p.setWin(53); p.setDraw(24); p.setLoss(22);
        p.setBtts(51); p.setOver15(74); p.setOver25(49); p.setOver35(27);
        req.setPredictions(p);

        byte[] bytes = svc.generateAnalysisPdf(req);
        assertNotNull(bytes);
        assertTrue(bytes.length > 500, "PDF should not be tiny");
        // Starts with %PDF
        String head = new String(bytes, 0, Math.min(bytes.length, 10), StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF"), "Should be a PDF file");
        // Because compression is disabled in fallback, watermark text should be present
        String asText = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(asText.contains("Powered by ChambuaVismart"), "Watermark text should be present");
        assertTrue(asText.contains("Fixture Analysis"), "Title text should be present");
    }
}
