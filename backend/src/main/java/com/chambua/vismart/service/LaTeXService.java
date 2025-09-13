package com.chambua.vismart.service;

import com.chambua.vismart.dto.AnalysisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

// iText 8 imports for fallback PDF generation
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.kernel.events.*;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.properties.UnitValue;

@Service
public class LaTeXService {
    private static final Logger log = LoggerFactory.getLogger(LaTeXService.class);

    public byte[] generateAnalysisPdf(AnalysisRequest req) throws IOException, InterruptedException {
        String template = loadTemplate();
        String tex = buildLatexFromTemplate(template, req);
        try {
            if (!isLatexAvailable()) {
                log.warn("latexmk not available on PATH; using iText fallback PDF generator");
                return buildRichPdfFallback(req);
            }
            return compileLatex(tex);
        } catch (Exception ex) {
            log.error("LaTeX compilation failed; using iText fallback PDF generator", ex);
            try {
                return buildRichPdfFallback(req);
            } catch (Exception inner) {
                log.error("iText fallback PDF generation also failed; returning minimal PDF bytes", inner);
                return minimalPdf(("Fixture Analysis: " + safe(req.getTeamA()!=null?req.getTeamA().getName():"") +
                        " vs " + safe(req.getTeamB()!=null?req.getTeamB().getName():""))).getBytes(StandardCharsets.UTF_8);
            }
        }
    }

    private String loadTemplate() throws IOException {
        ClassPathResource res = new ClassPathResource("templates/analysis.tex");
        try (InputStream is = res.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        // Basic LaTeX escaping for common characters
        return s.replace("\\", "\\textbackslash{}")
                .replace("_", "\\_")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("&", "\\&");
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String rows(List<AnalysisRequest.Row> list) {
        if (list == null || list.isEmpty()) return "\\midrule\\\\\n\\textit{No matches found} & & & \\ ";
        return list.stream().map(r -> String.format("%s & %s & %s & %s \\ ",
                esc(r.getYear()!=null? r.getYear().toString():""), esc(r.getDate()), esc(r.getMatch()), esc(r.getResult()))
        ).collect(Collectors.joining("\n"));
    }

    private String correctScores(List<AnalysisRequest.Score> list) {
        if (list == null || list.isEmpty()) return "0-0 & 0\\% \\";
        return list.stream().limit(10).map(s -> String.format("%s & %.1f\\%% \\ ", esc(s.getScore()), s.getProbability()))
                .collect(Collectors.joining("\n"));
    }

    private String buildLatexFromTemplate(String tpl, AnalysisRequest req) {
        String home = req.getTeamA()!=null? esc(req.getTeamA().getName()) : "Team A";
        String away = req.getTeamB()!=null? esc(req.getTeamB().getName()) : "Team B";
        String title = String.format("Fixture Analysis: %s vs %s", home, away);
        String h2hInsights = req.getH2h()!=null ? esc(safe(req.getH2h().getInsights())) : "";
        String last5A = req.getH2h()!=null && req.getH2h().getLast5TeamA()!=null ? esc(safe(req.getH2h().getLast5TeamA().getStreak())) : "0";
        String last5B = req.getH2h()!=null && req.getH2h().getLast5TeamB()!=null ? esc(safe(req.getH2h().getLast5TeamB().getStreak())) : "0";

        tpl = tpl.replace("{{TITLE}}", esc(title));
        tpl = tpl.replace("{{TOTAL_MATCHES}}", String.format("%,d", req.getTotalMatches()));
        // Team A block
        AnalysisRequest.Team a = req.getTeamA();
        AnalysisRequest.Team b = req.getTeamB();
        tpl = tpl.replace("{{TEAM_A_NAME}}", home)
                .replace("{{TEAM_A_MATCHES}}", String.valueOf(a!=null?a.getMatchesInvolved():0))
                .replace("{{TEAM_A_WINS}}", String.valueOf(a!=null?a.getWins():0))
                .replace("{{TEAM_A_DRAWS}}", String.valueOf(a!=null?a.getDraws():0))
                .replace("{{TEAM_A_LOSSES}}", String.valueOf(a!=null?a.getLosses():0))
                .replace("{{TEAM_A_BTTS}}", String.valueOf(a!=null?a.getBtts():0))
                .replace("{{TEAM_A_OVER15}}", String.valueOf(a!=null?a.getOver15():0))
                .replace("{{TEAM_A_OVER25}}", String.valueOf(a!=null?a.getOver25():0));
        tpl = tpl.replace("{{TEAM_B_NAME}}", away)
                .replace("{{TEAM_B_MATCHES}}", String.valueOf(b!=null?b.getMatchesInvolved():0))
                .replace("{{TEAM_B_WINS}}", String.valueOf(b!=null?b.getWins():0))
                .replace("{{TEAM_B_DRAWS}}", String.valueOf(b!=null?b.getDraws():0))
                .replace("{{TEAM_B_LOSSES}}", String.valueOf(b!=null?b.getLosses():0))
                .replace("{{TEAM_B_BTTS}}", String.valueOf(b!=null?b.getBtts():0))
                .replace("{{TEAM_B_OVER15}}", String.valueOf(b!=null?b.getOver15():0))
                .replace("{{TEAM_B_OVER25}}", String.valueOf(b!=null?b.getOver25():0));

        // H2H insights and GD
        double gdAgg = req.getH2h()!=null? req.getH2h().getGoalDifferential(): 0;
        double gdAvg = req.getH2h()!=null? req.getH2h().getAverageGD(): 0;
        tpl = tpl.replace("{{H2H_INSIGHTS}}", h2hInsights)
                .replace("{{GD_PERSPECTIVE}}", String.format("%s", home))
                .replace("{{GD_AGG}}", String.format("%s", (gdAgg>=0? "+"+((int)gdAgg): String.valueOf((int)gdAgg))))
                .replace("{{GD_AVG}}", String.format("%.2f", gdAvg))
                .replace("{{LAST5_A}}", last5A)
                .replace("{{LAST5_B}}", last5B);

        // Tables
        tpl = tpl.replace("{{H2H_HISTORY_ROWS}}", rows(req.getH2h()!=null? req.getH2h().getHistory(): null));
        tpl = tpl.replace("{{H2H_ALL_ROWS}}", rows(req.getH2h()!=null? req.getH2h().getAllOrientations(): null));

        // Predictions
        AnalysisRequest.Predictions p = req.getPredictions();
        tpl = tpl.replace("{{A_WIN}}", String.valueOf(p!=null?p.getWin():0))
                .replace("{{DRAW}}", String.valueOf(p!=null?p.getDraw():0))
                .replace("{{B_WIN}}", String.valueOf(p!=null?p.getLoss():0))
                .replace("{{BTTS}}", String.valueOf(p!=null?p.getBtts():0))
                .replace("{{OVER15}}", String.valueOf(p!=null?p.getOver15():0))
                .replace("{{OVER25}}", String.valueOf(p!=null?p.getOver25():0))
                .replace("{{OVER35}}", String.valueOf(p!=null?p.getOver35():0))
                .replace("{{CORRECT_SCORES_ROWS}}", correctScores(p!=null? p.getCorrectScores(): null));

        return tpl;
    }

    private boolean isLatexAvailable() {
        try {
            // Allow tests/environments to force-disable LaTeX by property or env
            String force = System.getProperty("DISABLE_LATEXMK", System.getenv("DISABLE_LATEXMK"));
            if (force != null && (force.equalsIgnoreCase("true") || force.equals("1"))) return false;
            // Check common UNIX path
            if (new File("/usr/bin/latexmk").exists()) return true;
            // Check common Windows TeX Live path(s)
            String[] candidates = new String[] {
                    "C:\\texlive\\2025\\bin\\win32\\latexmk.exe",
                    "C:\\texlive\\2024\\bin\\win32\\latexmk.exe",
                    "C:\\texlive\\2023\\bin\\win32\\latexmk.exe"
            };
            for (String p : candidates) { if (new File(p).exists()) return true; }
            // Try invoking on PATH
            Process proc = new ProcessBuilder("latexmk", "--version").redirectErrorStream(true).start();
            int code = proc.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] compileLatex(String texContent) throws IOException, InterruptedException {
        Path tmpDir = Files.createTempDirectory("analysis-");
        Path tex = tmpDir.resolve("analysis.tex");
        Files.writeString(tex, texContent, StandardCharsets.UTF_8);
        String latexmkCmd = "latexmk";
        // Prefer full Windows path if present
        String[] winPaths = new String[] {
                "C:\\texlive\\2025\\bin\\win32\\latexmk.exe",
                "C:\\texlive\\2024\\bin\\win32\\latexmk.exe",
                "C:\\texlive\\2023\\bin\\win32\\latexmk.exe"
        };
        for (String w : winPaths) { if (new File(w).exists()) { latexmkCmd = w; break; } }
        String[] cmd = new String[] { latexmkCmd, "-pdf", "-interaction=nonstopmode", "-halt-on-error", "analysis.tex" };
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(tmpDir.toFile());
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        String logOut;
        try (InputStream is = pr.getInputStream()) {
            logOut = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code = pr.waitFor();
        if (code != 0) {
            log.error("latexmk failed (code={})\n{}", code, logOut);
            throw new IOException("latexmk failed with code " + code);
        }
        Path pdf = tmpDir.resolve("analysis.pdf");
        byte[] bytes = Files.readAllBytes(pdf);
        // cleanup (best-effort)
        try {
            Files.walk(tmpDir)
                    .sorted((a,b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
        return bytes;
    }

    private byte[] buildSimplePdfFallback(AnalysisRequest req) throws IOException {
        // Build a very simple but valid single-page PDF with core sections and a diagonal watermark, no external libraries
        String home = safe(req.getTeamA()!=null? req.getTeamA().getName(): "Team A");
        String away = safe(req.getTeamB()!=null? req.getTeamB().getName(): "Team B");
        String title = "Fixture Analysis: " + home + " vs " + away;
        StringBuilder body = new StringBuilder();
        body.append("Total Matches Played: ").append(req.getTotalMatches()).append("\\n\\n");
        AnalysisRequest.Team a = req.getTeamA();
        AnalysisRequest.Team b = req.getTeamB();
        body.append("Team Summaries\\n");
        if (a != null) body.append(home).append(": MI=").append(a.getMatchesInvolved()).append(", W=").append(a.getWins()).append(", D=").append(a.getDraws()).append(", L=").append(a.getLosses()).append(", BTTS=").append(a.getBtts()).append(", O1.5=").append(a.getOver15()).append(", O2.5=").append(a.getOver25()).append("\\n");
        if (b != null) body.append(away).append(": MI=").append(b.getMatchesInvolved()).append(", W=").append(b.getWins()).append(", D=").append(b.getDraws()).append(", L=").append(b.getLosses()).append(", BTTS=").append(b.getBtts()).append(", O1.5=").append(b.getOver15()).append(", O2.5=").append(b.getOver25()).append("\\n\\n");
        if (req.getH2h()!=null) {
            body.append("H2H Insights: ").append(safe(req.getH2h().getInsights())).append("\\n");
            body.append("GD (").append(home).append(" perspective): ").append(req.getH2h().getGoalDifferential()).append(", Avg GD: ").append(req.getH2h().getAverageGD()).append("\\n");
            body.append(home).append(" Last 5: ").append(req.getH2h().getLast5TeamA()!=null? safe(req.getH2h().getLast5TeamA().getStreak()):"0").append("\\n");
            body.append(away).append(" Last 5: ").append(req.getH2h().getLast5TeamB()!=null? safe(req.getH2h().getLast5TeamB().getStreak()):"0").append("\\n\\n");
        }
        if (req.getPredictions()!=null) {
            var p = req.getPredictions();
            body.append("Predictions\\n");
            body.append(home).append(" Win: ").append(p.getWin()).append("%\\n");
            body.append("Draw: ").append(p.getDraw()).append("%\\n");
            body.append(away).append(" Win: ").append(p.getLoss()).append("%\\n");
            body.append("BTTS: ").append(p.getBtts()).append("%\\n");
            body.append("Over 1.5: ").append(p.getOver15()).append("%\\n");
            body.append("Over 2.5: ").append(p.getOver25()).append("%\\n");
            body.append("Over 3.5: ").append(p.getOver35()).append("%\\n");
            if (p.getCorrectScores()!=null && !p.getCorrectScores().isEmpty()) {
                body.append("Top Correct Scores: ");
                for (int i=0;i<Math.min(3, p.getCorrectScores().size());i++) {
                    var s = p.getCorrectScores().get(i);
                    body.append(s.getScore()).append(" (").append(String.format(java.util.Locale.US, "%.1f", s.getProbability())).append("%)");
                    if (i<Math.min(3,p.getCorrectScores().size())-1) body.append(", ");
                }
                body.append("\\n");
            }
        }
        // Encode text into PDF content stream, add a watermark background text
        String contentText = body.toString().replace("(", "[").replace(")", "]").replace("\\", "\\\\");
        String watermark = "Powered by ChambuaVismart";
        String stream = "q 0.9 g BT /F1 16 Tf 72 760 Td ("+ title +") Tj ET Q\n" +
                // watermark: light gray rotated text across the page center
                "q 0.8 g 0.2 G 0.2 g 0.7071 0.7071 -0.7071 0.7071 200 100 cm BT /F1 48 Tf 0 0 Td ("+ watermark +") Tj ET Q\n" +
                "q 0 g BT /F1 10 Tf 72 720 Td ("+ contentText +") Tj ET Q\n";
        byte[] streamBytes = ("<< /Length " + stream.getBytes(StandardCharsets.UTF_8).length + " >>\nstream\n" + stream + "\nendstream").getBytes(StandardCharsets.UTF_8);
        int len = streamBytes.length;
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        int xrefStart;
        java.util.List<Integer> offsets = new java.util.ArrayList<>();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.OutputStream out = baos;
        // helper to write and track offsets
        java.util.function.BiConsumer<Integer, String> writeObj = (num, obj) -> {
            try {
                offsets.add(baos.size());
                out.write((num + " 0 obj\n").getBytes(StandardCharsets.UTF_8));
                out.write(obj.getBytes(StandardCharsets.UTF_8));
                out.write("\nendobj\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        };
        try {
            out.write("%PDF-1.4\n".getBytes(StandardCharsets.UTF_8));
            // 1: Catalog
            writeObj.accept(1, "<< /Type /Catalog /Pages 2 0 R >>");
            // 2: Pages
            writeObj.accept(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
            // 3: Page
            writeObj.accept(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>");
            // 4: Contents
            offsets.add(baos.size());
            out.write("4 0 obj\n".getBytes(StandardCharsets.UTF_8));
            out.write(("<< /Length " + len + " >>\n").getBytes(StandardCharsets.UTF_8));
            out.write("stream\n".getBytes(StandardCharsets.UTF_8));
            out.write(stream.getBytes(StandardCharsets.UTF_8));
            out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.UTF_8));
            // 5: Font
            writeObj.accept(5, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
            // xref
            int xref = baos.size();
            out.write(("xref\n0 6\n0000000000 65535 f \n").getBytes(StandardCharsets.UTF_8));
            int[] offs = new int[6];
            offs[1] = getOffset(offsets, 1);
            offs[2] = getOffset(offsets, 2);
            offs[3] = getOffset(offsets, 3);
            offs[4] = getOffset(offsets, 4);
            offs[5] = getOffset(offsets, 5);
            for (int i=1;i<=5;i++) {
                out.write(String.format(java.util.Locale.US, "%010d 00000 n \n", offs[i]).getBytes(StandardCharsets.UTF_8));
            }
            out.write(("trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            return minimalPdf(title).getBytes(StandardCharsets.UTF_8);
        }
        return baos.toByteArray();
    }

    private int getOffset(java.util.List<Integer> offs, int idx) {
        if (idx-1 < 0 || idx-1 >= offs.size()) return 0;
        return offs.get(idx-1);
    }

    private String minimalPdf(String title) {
        // Very small valid PDF (not LaTeX) as a fallback to keep UX flowing in environments without latexmk
        return "%PDF-1.4\n" +
                "1 0 obj<<>>endobj\n" +
                "2 0 obj<< /Length 44 >>stream\nBT /F1 12 Tf 72 720 Td (" + title.replace("(", "[").replace(")","[") + ") Tj ET\nendstream endobj\n" +
                "3 0 obj<< /Type /Page /Parent 4 0 R /MediaBox [0 0 595 842] /Contents 2 0 R /Resources << /Font << /F1 5 0 R >> >> >>endobj\n" +
                "4 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n" +
                "5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n" +
                "6 0 obj<< /Type /Catalog /Pages 4 0 R >>endobj\n" +
                "xref\n0 7\n0000000000 65535 f \n0000000010 00000 n \n0000000053 00000 n \n0000000131 00000 n \n0000000265 00000 n \n0000000323 00000 n \n0000000390 00000 n \ntrailer<< /Size 7 /Root 6 0 R >>\nstartxref\n447\n%%EOF";
    }

    // iText 8 rich fallback PDF generation with sections, tables, and watermark
    private byte[] buildRichPdfFallback(AnalysisRequest req) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        // Disable compression to keep text searchable for tests/debug
        writer.setCompressionLevel(0);
        PdfDocument pdf = new PdfDocument(writer);
        // Watermark on every page
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new IEventHandler() {
            @Override
            public void handleEvent(Event event) {
                PdfDocumentEvent de = (PdfDocumentEvent) event;
                com.itextpdf.kernel.geom.Rectangle pageSize = de.getPage().getPageSize();
                float cx = pageSize.getWidth() / 2;
                float cy = pageSize.getHeight() / 2;
                PdfCanvas pc = new PdfCanvas(de.getPage());
                pc.saveState();
                PdfExtGState gs = new PdfExtGState();
                gs.setFillOpacity(0.2f);
                pc.setExtGState(gs);
                Canvas canvas = new Canvas(pc, pageSize);
                String wm = "Powered by ChambuaVismart";
                canvas.showTextAligned(wm, cx, cy, TextAlignment.CENTER, VerticalAlignment.MIDDLE, (float) Math.toRadians(45));
                canvas.close();
                pc.restoreState();
            }
        });
        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(72, 72, 72, 72); // 1 inch

        String home = safe(req.getTeamA()!=null? req.getTeamA().getName(): "Team A");
        String away = safe(req.getTeamB()!=null? req.getTeamB().getName(): "Team B");
        // Title
        doc.add(new Paragraph("Fixture Analysis: " + home + " vs " + away)
                .setFontSize(16).setBold().setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Total Matches Played: " + String.format("%,d", req.getTotalMatches())).setMarginTop(8));

        // Section: Team Summaries
        doc.add(new Paragraph("Team Summaries").setBold().setMarginTop(12));
        if (req.getTeamA()!=null) {
            doc.add(new Paragraph(home).setBold());
            doc.add(buildTeamTable(req.getTeamA()));
        }
        if (req.getTeamB()!=null) {
            doc.add(new Paragraph(away).setBold().setMarginTop(8));
            doc.add(buildTeamTable(req.getTeamB()));
        }

        // Section: H2H Insights
        doc.add(new Paragraph("H2H Insights").setBold().setMarginTop(12));
        if (req.getH2h()!=null) {
            AnalysisRequest.H2H h = req.getH2h();
            if (h.getInsights()!=null && !h.getInsights().isBlank()) {
                doc.add(new Paragraph(h.getInsights()));
            }
            doc.add(new Paragraph(String.format("Goal Differential (%s perspective): %s, Average GD: %.2f",
                    home,
                    (h.getGoalDifferential()>=0? "+"+((int)h.getGoalDifferential()): String.valueOf((int)h.getGoalDifferential())),
                    h.getAverageGD())));
            if (h.getLast5TeamA()!=null) {
                var l5a = h.getLast5TeamA();
                doc.add(new Paragraph(String.format("%s Last 5: %s, Win rate: %d%%, Points: %d",
                        home, safe(l5a.getStreak()), l5a.getWinRate(), l5a.getPoints())));
            }
            if (h.getLast5TeamB()!=null) {
                var l5b = h.getLast5TeamB();
                doc.add(new Paragraph(String.format("%s Last 5: %s, Win rate: %d%%, Points: %d",
                        away, safe(l5b.getStreak()), l5b.getWinRate(), l5b.getPoints())));
            }
        } else {
            doc.add(new Paragraph("No H2H insights available."));
        }

        // Section: Head-to-Head Results
        doc.add(new Paragraph("Head-to-Head Results").setBold().setMarginTop(12));
        doc.add(new Paragraph("Chosen orientation history").setItalic());
        doc.add(buildH2HTable(req.getH2h()!=null? req.getH2h().getHistory(): null));
        doc.add(new Paragraph("All orientations").setItalic().setMarginTop(6));
        doc.add(buildH2HTable(req.getH2h()!=null? req.getH2h().getAllOrientations(): null));

        // Section: Fixture Analysis Results
        doc.add(new Paragraph("Fixture Analysis Results").setBold().setMarginTop(12));
        doc.add(buildResultsTable(home, away, req.getPredictions()));

        // Correct Scores
        doc.add(new Paragraph("Most Probable Correct Scores").setBold().setMarginTop(8));
        doc.add(buildScoresTable(req.getPredictions()!=null? req.getPredictions().getCorrectScores(): null));

        doc.close();
        return baos.toByteArray();
    }

    private Table buildTeamTable(AnalysisRequest.Team t) {
        Table table = new Table(new float[]{3, 2});
        table.setWidth(UnitValue.createPercentValue(100));
        addKV(table, "Matches Involved", String.valueOf(t.getMatchesInvolved()));
        addKV(table, "Wins", String.valueOf(t.getWins()));
        addKV(table, "Draws", String.valueOf(t.getDraws()));
        addKV(table, "Losses", String.valueOf(t.getLosses()));
        addKV(table, "BTTS", String.valueOf(t.getBtts()));
        addKV(table, "Over 1.5", String.valueOf(t.getOver15()));
        addKV(table, "Over 2.5", String.valueOf(t.getOver25()));
        return table;
    }

    private void addKV(Table table, String k, String v) {
        table.addCell(new Cell().add(new Paragraph(k)).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        table.addCell(new Cell().add(new Paragraph(v)).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
    }

    private Table buildH2HTable(java.util.List<AnalysisRequest.Row> rows) {
        Table t = new Table(new float[]{1.1f, 1.8f, 3.5f, 1.6f});
        t.setWidth(UnitValue.createPercentValue(100));
        t.addHeaderCell(new Cell().add(new Paragraph("Year").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Date").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Match").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Result").setBold()));
        if (rows == null || rows.isEmpty()) {
            t.addCell(new Cell(1,4).add(new Paragraph("No matches found")).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
            return t;
        }
        for (AnalysisRequest.Row r : rows) {
            t.addCell(new Cell().add(new Paragraph(r.getYear()!=null? r.getYear().toString(): "")).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
            t.addCell(new Cell().add(new Paragraph(safe(r.getDate()))).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
            t.addCell(new Cell().add(new Paragraph(safe(r.getMatch()))).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
            t.addCell(new Cell().add(new Paragraph(safe(r.getResult()))).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        }
        return t;
    }

    private Table buildResultsTable(String home, String away, AnalysisRequest.Predictions p) {
        Table t = new Table(new float[]{3.5f, 1.2f});
        t.setWidth(UnitValue.createPercentValue(100));
        t.addHeaderCell(new Cell().add(new Paragraph("Outcome").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Probability").setBold()));
        if (p == null) {
            t.addCell(new Cell(1,2).add(new Paragraph("No predictions available")));
            return t;
        }
        t.addCell(new Cell().add(new Paragraph(home + " Win")));
        t.addCell(new Cell().add(new Paragraph(p.getWin() + "%")));
        t.addCell(new Cell().add(new Paragraph("Draw")));
        t.addCell(new Cell().add(new Paragraph(p.getDraw() + "%")));
        t.addCell(new Cell().add(new Paragraph(away + " Win")));
        t.addCell(new Cell().add(new Paragraph(p.getLoss() + "%")));
        t.addCell(new Cell().add(new Paragraph("Both Teams To Score (BTTS)")));
        t.addCell(new Cell().add(new Paragraph(p.getBtts() + "%")));
        t.addCell(new Cell().add(new Paragraph("Over 1.5 Goals")));
        t.addCell(new Cell().add(new Paragraph(p.getOver15() + "%")));
        t.addCell(new Cell().add(new Paragraph("Over 2.5 Goals")));
        t.addCell(new Cell().add(new Paragraph(p.getOver25() + "%")));
        t.addCell(new Cell().add(new Paragraph("Over 3.5 Goals")));
        t.addCell(new Cell().add(new Paragraph(p.getOver35() + "%")));
        return t;
    }

    private Table buildScoresTable(java.util.List<AnalysisRequest.Score> list) {
        Table t = new Table(new float[]{2.0f, 1.0f});
        t.setWidth(UnitValue.createPercentValue(60));
        t.addHeaderCell(new Cell().add(new Paragraph("Score").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Probability").setBold()));
        if (list == null || list.isEmpty()) {
            t.addCell(new Cell(1,2).add(new Paragraph("No scores available")));
            return t;
        }
        int limit = Math.min(10, list.size());
        for (int i=0; i<limit; i++) {
            AnalysisRequest.Score s = list.get(i);
            t.addCell(new Cell().add(new Paragraph(safe(s.getScore()))));
            t.addCell(new Cell().add(new Paragraph(String.format(java.util.Locale.US, "%.1f%%", s.getProbability()))));
        }
        return t;
    }
}
