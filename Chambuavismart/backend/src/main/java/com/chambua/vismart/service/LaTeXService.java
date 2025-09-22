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
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.kernel.events.*;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
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

    private String rowsLimited(List<AnalysisRequest.Row> list, int limit) {
        if (list == null || list.isEmpty()) return "\\midrule\\\\\n\\textit{No matches found} & & & \\ ";
        int size = list.size();
        int from = Math.max(0, size - limit);
        return list.subList(from, size).stream().map(r -> String.format("%s & %s & %s & %s \\ ",
                esc(r.getYear()!=null? r.getYear().toString():""), esc(r.getDate()), esc(r.getMatch()), esc(r.getResult()))
        ).collect(Collectors.joining("\n"));
    }

    private String correctScores(List<AnalysisRequest.Score> list) {
        if (list == null || list.isEmpty()) return "0-0 & 0\\% \\";
        return list.stream().limit(5).map(s -> String.format("%s & %.1f\\%% \\ ", esc(s.getScore()), s.getProbability()))
                .collect(Collectors.joining("\n"));
    }

    private String buildLatexFromTemplate(String tpl, AnalysisRequest req) {
        String home = req.getTeamA()!=null? esc(req.getTeamA().getName()) : "Team A";
        String away = req.getTeamB()!=null? esc(req.getTeamB().getName()) : "Team B";
        String title = String.format("Fixture Analysis: %s vs %s", home, away);
        String h2hInsights = req.getH2h()!=null ? esc(safe(req.getH2h().getInsights())) : "";
        // Append streak insight summaries if present
        if (req.getH2h()!=null) {
            String siA = req.getH2h().getStreakInsightA();
            String siB = req.getH2h().getStreakInsightB();
            StringBuilder extra = new StringBuilder();
            if (siA != null && !siA.isBlank()) extra.append("\\n\\n").append(esc(siA));
            if (siB != null && !siB.isBlank()) extra.append("\\n").append(esc(siB));
            if (extra.length() > 0) {
                if (h2hInsights == null || h2hInsights.isBlank()) h2hInsights = extra.toString();
                else h2hInsights = h2hInsights + extra.toString();
            }
        }
        String last5A = req.getH2h()!=null && req.getH2h().getLast5TeamA()!=null ? esc(safe(req.getH2h().getLast5TeamA().getStreak())) : "";
        String last5B = req.getH2h()!=null && req.getH2h().getLast5TeamB()!=null ? esc(safe(req.getH2h().getLast5TeamB().getStreak())) : "";

        // Team color mapping (simple, extendable)
        java.util.Map<String,String> colorHex = new java.util.HashMap<>();
        colorHex.put("crystal palace", "1B458F"); // Blue
        colorHex.put("liverpool", "C8102E");      // Red
        colorHex.put("everton", "003399");        // Blue
        String teamAHex = colorHex.getOrDefault(safe(req.getTeamA()!=null? req.getTeamA().getName():"").toLowerCase(java.util.Locale.ROOT), "228B22");
        String teamBHex = colorHex.getOrDefault(safe(req.getTeamB()!=null? req.getTeamB().getName():"").toLowerCase(java.util.Locale.ROOT), "228B22");

        tpl = tpl.replace("{{TITLE}}", esc(title));
        tpl = tpl.replace("{{TOTAL_MATCHES}}", String.format("%,d", req.getTotalMatches()));
        tpl = tpl.replace("{{TEAM_A_NAME}}", home).replace("{{TEAM_B_NAME}}", away);
        tpl = tpl.replace("{{TEAM_A_HEX}}", teamAHex).replace("{{TEAM_B_HEX}}", teamBHex);

        // Fixture kickoff (only when initiated from fixtures/home and date provided)
        String kickoff = "";
        try {
            String src = req.getSource();
            String fx = req.getFixtureDate();
            if (fx != null && !fx.isBlank() && src != null && (src.equalsIgnoreCase("fixtures") || src.equalsIgnoreCase("home") || src.equalsIgnoreCase("home-today") || src.equalsIgnoreCase("today"))) {
                java.time.ZoneId tz = java.time.ZoneId.systemDefault();
                java.time.format.DateTimeFormatter dtFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
                java.time.OffsetDateTime odt = null;
                java.time.LocalDateTime ldt = null;
                java.time.LocalDate ld = null;
                try { odt = java.time.OffsetDateTime.parse(fx); } catch (Exception ignore) {}
                if (odt != null) {
                    kickoff = odt.atZoneSameInstant(tz).format(dtFmt);
                } else {
                    try { ldt = java.time.LocalDateTime.parse(fx); } catch (Exception ignore) {}
                    if (ldt != null) {
                        kickoff = ldt.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(tz).format(dtFmt);
                    } else {
                        try { ld = java.time.LocalDate.parse(fx); } catch (Exception ignore) {}
                        if (ld != null) {
                            kickoff = ld.atStartOfDay(tz).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        tpl = tpl.replace("{{FIXTURE_WHEN}}", esc(kickoff));

        // Summaries with percentages (count and percent of matches involved)
        AnalysisRequest.Team a = req.getTeamA();
        AnalysisRequest.Team b = req.getTeamB();
        int aMI = a!=null? a.getMatchesInvolved(): 0;
        int bMI = b!=null? b.getMatchesInvolved(): 0;
        java.util.function.BiFunction<Integer,Integer,String> fmt = (val, total) -> {
            if (total == 0 || val == null) return String.valueOf(val!=null? val:0);
            double pct = Math.round((val * 10000.0) / total) / 100.0; // 2 decimals
            return String.format(java.util.Locale.US, "%d (%.0f\\%%)", val, Math.floor(pct+0.5));
        };
        tpl = tpl.replace("{{A_MI}}", String.valueOf(aMI))
                 .replace("{{B_MI}}", String.valueOf(bMI))
                 .replace("{{A_WINS_FMT}}", fmt.apply(a!=null? a.getWins():0, aMI))
                 .replace("{{B_WINS_FMT}}", fmt.apply(b!=null? b.getWins():0, bMI))
                 .replace("{{A_DRAWS_FMT}}", fmt.apply(a!=null? a.getDraws():0, aMI))
                 .replace("{{B_DRAWS_FMT}}", fmt.apply(b!=null? b.getDraws():0, bMI))
                 .replace("{{A_LOSSES_FMT}}", fmt.apply(a!=null? a.getLosses():0, aMI))
                 .replace("{{B_LOSSES_FMT}}", fmt.apply(b!=null? b.getLosses():0, bMI))
                 .replace("{{A_BTTS_FMT}}", fmt.apply(a!=null? a.getBtts():0, aMI))
                 .replace("{{B_BTTS_FMT}}", fmt.apply(b!=null? b.getBtts():0, bMI))
                 .replace("{{A_OVER15_FMT}}", fmt.apply(a!=null? a.getOver15():0, aMI))
                 .replace("{{B_OVER15_FMT}}", fmt.apply(b!=null? b.getOver15():0, bMI))
                 .replace("{{A_OVER25_FMT}}", fmt.apply(a!=null? a.getOver25():0, aMI))
                 .replace("{{B_OVER25_FMT}}", fmt.apply(b!=null? b.getOver25():0, bMI));

        // H2H insights and GD (inline)
        double gdAgg = req.getH2h()!=null? req.getH2h().getGoalDifferential(): 0;
        double gdAvg = req.getH2h()!=null? req.getH2h().getAverageGD(): 0;
        tpl = tpl.replace("{{H2H_INSIGHTS}}", h2hInsights)
                .replace("{{GD_PERSPECTIVE}}", String.format("%s", home))
                .replace("{{GD_AGG}}", String.format("%s", (gdAgg>=0? "+"+((int)gdAgg): String.valueOf((int)gdAgg))))
                .replace("{{GD_AVG}}", String.format(java.util.Locale.US, "%.2f", gdAvg))
                .replace("{{LAST5_A}}", last5A)
                .replace("{{LAST5_B}}", last5B);

        // Tables with stricter limits (single-page)
        int histLimit = 5;
        int allLimit = 7;
        List<AnalysisRequest.Row> hist = req.getH2h()!=null? req.getH2h().getHistory(): null;
        List<AnalysisRequest.Row> all = req.getH2h()!=null? req.getH2h().getAllOrientations(): null;
        tpl = tpl.replace("{{H2H_HISTORY_ROWS}}", rowsLimited(hist, histLimit));
        tpl = tpl.replace("{{H2H_ALL_ROWS}}", rowsLimited(all, allLimit));
        tpl = tpl.replace("{{H2H_HISTORY_LIMIT}}", String.valueOf(histLimit));
        tpl = tpl.replace("{{H2H_ALL_LIMIT}}", String.valueOf(allLimit));
        String histNote = (hist!=null && hist.size()>histLimit) ? "\\textit{... earlier matches available in app}" : "";
        String allNote = (all!=null && all.size()>allLimit) ? "\\textit{... earlier matches available in app}" : "";
        tpl = tpl.replace("{{H2H_HISTORY_NOTE}}", histNote);
        tpl = tpl.replace("{{H2H_ALL_NOTE}}", allNote);

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

    // iText 8 rich fallback PDF generation with themed styling, compact layout, and progress bars
    private byte[] buildRichPdfFallback(AnalysisRequest req) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        writer.setCompressionLevel(0); // keep searchable
        PdfDocument pdf = new PdfDocument(writer);

        // Colors
        final DeviceRgb FOOTBALL_GREEN = new DeviceRgb(34,139,34);
        final DeviceRgb PITCH_BAR = new DeviceRgb(76,175,80);
        final DeviceRgb DARK_GREEN = new DeviceRgb(0,100,0);
        final DeviceRgb TEAM_RED = new DeviceRgb(200,16,46);
        final DeviceRgb TEAM_BLUE = new DeviceRgb(0,51,160);
        final DeviceRgb ROW_ALT = new DeviceRgb(235,244,235);

        // Pitch background lines and watermark on every page
        pdf.addEventHandler(PdfDocumentEvent.START_PAGE, new IEventHandler() {
            @Override
            public void handleEvent(Event event) {
                PdfDocumentEvent de = (PdfDocumentEvent) event;
                Rectangle ps = de.getPage().getPageSize();
                PdfCanvas pc = new PdfCanvas(de.getPage());
                pc.saveState();
                // faint pitch lines
                PdfExtGState gs = new PdfExtGState();
                gs.setStrokeOpacity(0.08f);
                pc.setExtGState(gs);
                pc.setStrokeColor(new DeviceRgb(144,238,144)); // light green
                float step = 36f; // every 0.5 inch
                for (float y = ps.getBottom() + step; y < ps.getTop(); y += step) {
                    pc.moveTo(ps.getLeft(), y);
                    pc.lineTo(ps.getRight(), y);
                }
                pc.stroke();
                // center circle
                float cx = ps.getWidth()/2f; float cy = ps.getHeight()/2f;
                pc.circle(cx, cy, 36f);
                pc.stroke();
                pc.restoreState();
            }
        });
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new IEventHandler() {
            @Override
            public void handleEvent(Event event) {
                PdfDocumentEvent de = (PdfDocumentEvent) event;
                Rectangle pageSize = de.getPage().getPageSize();
                float cx = pageSize.getWidth() / 2;
                float cy = pageSize.getHeight() / 2;
                PdfCanvas pc = new PdfCanvas(de.getPage());
                pc.saveState();
                PdfExtGState gs = new PdfExtGState();
                gs.setFillOpacity(0.15f);
                pc.setExtGState(gs);
                Canvas canvas = new Canvas(pc, pageSize);
                pc.setFillColor(FOOTBALL_GREEN);
                String wm = "Powered by ChambuaVismart";
                canvas.showTextAligned(wm, cx, cy, TextAlignment.CENTER, VerticalAlignment.MIDDLE, (float) Math.toRadians(25));
                canvas.close();
                pc.restoreState();
            }
        });

        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 36, 36); // 0.5 inch

        String home = safe(req.getTeamA()!=null? req.getTeamA().getName(): "Team A");
        String away = safe(req.getTeamB()!=null? req.getTeamB().getName(): "Team B");

        // Title bar
        Paragraph title = new Paragraph("Fixture Analysis: " + home + " vs " + away)
                .setFontSize(16).setBold().setFontColor(DeviceRgb.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(6)
                .setBackgroundColor(PITCH_BAR);
        doc.add(title);
        // Fixture kickoff line (if available from fixtures/home context)
        try {
            String src = req.getSource();
            String fx = req.getFixtureDate();
            String kickoff = null;
            if (fx != null && !fx.isBlank() && src != null && (src.equalsIgnoreCase("fixtures") || src.equalsIgnoreCase("home") || src.equalsIgnoreCase("home-today") || src.equalsIgnoreCase("today"))) {
                java.time.ZoneId tz = java.time.ZoneId.systemDefault();
                java.time.format.DateTimeFormatter dtFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
                java.time.OffsetDateTime odt = null; java.time.LocalDateTime ldt = null; java.time.LocalDate ld = null;
                try { odt = java.time.OffsetDateTime.parse(fx); } catch (Exception ignore) {}
                if (odt != null) kickoff = odt.atZoneSameInstant(tz).format(dtFmt);
                else {
                    try { ldt = java.time.LocalDateTime.parse(fx); } catch (Exception ignore) {}
                    if (ldt != null) kickoff = ldt.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(tz).format(dtFmt);
                    else {
                        try { ld = java.time.LocalDate.parse(fx); } catch (Exception ignore) {}
                        if (ld != null) kickoff = ld.atStartOfDay(tz).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    }
                }
            }
            if (kickoff != null && !kickoff.isBlank()) {
                doc.add(new Paragraph("Fixture kickoff: " + kickoff).setFontSize(9));
            }
        } catch (Exception ignored) {}
        doc.add(new Paragraph("Total Matches Played: " + String.format("%,d", req.getTotalMatches()))
                .setFontSize(10).setMarginTop(2));

        // Team summaries: single 3-column with percentages
        // Map team colors
        java.util.function.Function<String, DeviceRgb> teamColor = (name) -> {
            if (name == null) return FOOTBALL_GREEN;
            String n = name.toLowerCase(java.util.Locale.ROOT);
            if (n.contains("liverpool")) return new DeviceRgb(200,16,46);
            if (n.contains("everton")) return new DeviceRgb(0,51,153);
            if (n.contains("crystal palace")) return new DeviceRgb(27,69,143);
            return FOOTBALL_GREEN;
        };
        DeviceRgb teamAColor = teamColor.apply(home);
        DeviceRgb teamBColor = teamColor.apply(away);
        Paragraph tsHdr = new Paragraph("Team Summaries").setBold().setFontSize(12).setFontColor(FOOTBALL_GREEN).setMarginTop(4).setMarginBottom(2);
        doc.add(tsHdr);
        Table ts = new Table(new float[]{3.6f, 2.8f, 2.8f}).setWidth(UnitValue.createPercentValue(100));
        ts.addHeaderCell(new Cell().add(new Paragraph("Stat").setBold()));
        ts.addHeaderCell(new Cell().add(new Paragraph(home).setBold().setFontColor(teamAColor)));
        ts.addHeaderCell(new Cell().add(new Paragraph(away).setBold().setFontColor(teamBColor)));
        AnalysisRequest.Team ta = req.getTeamA();
        AnalysisRequest.Team tb = req.getTeamB();
        int aMI = ta!=null? ta.getMatchesInvolved():0;
        int bMI = tb!=null? tb.getMatchesInvolved():0;
        java.util.function.BiFunction<Integer,Integer,String> fmt = (val, total) -> {
            int v = (val==null?0:val);
            if (total <= 0) return String.valueOf(v);
            double pct = Math.round((v*10000.0)/total)/100.0;
            return String.format(java.util.Locale.US, "%d (%.0f%%)", v, Math.floor(pct+0.5));
        };
        ts.addCell(new Cell().add(new Paragraph("Matches Involved")));
        ts.addCell(new Cell().add(new Paragraph(String.valueOf(aMI))));
        ts.addCell(new Cell().add(new Paragraph(String.valueOf(bMI))));
        ts.addCell(new Cell().add(new Paragraph("Wins")));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(ta!=null? ta.getWins():0, aMI))));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(tb!=null? tb.getWins():0, bMI))));
        ts.addCell(new Cell().add(new Paragraph("Draws")));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(ta!=null? ta.getDraws():0, aMI))));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(tb!=null? tb.getDraws():0, bMI))));
        ts.addCell(new Cell().add(new Paragraph("Losses")));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(ta!=null? ta.getLosses():0, aMI))));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(tb!=null? tb.getLosses():0, bMI))));
        ts.addCell(new Cell().add(new Paragraph("BTTS")));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(ta!=null? ta.getBtts():0, aMI))));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(tb!=null? tb.getBtts():0, bMI))));
        ts.addCell(new Cell().add(new Paragraph("Over 1.5")));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(ta!=null? ta.getOver15():0, aMI))));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(tb!=null? tb.getOver15():0, bMI))));
        ts.addCell(new Cell().add(new Paragraph("Over 2.5")));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(ta!=null? ta.getOver25():0, aMI))));
        ts.addCell(new Cell().add(new Paragraph(fmt.apply(tb!=null? tb.getOver25():0, bMI))));
        ts.setFontSize(8);
        doc.add(ts);

        // H2H insights condensed
        Paragraph h2hHdr = new Paragraph("H2H Insights").setBold().setFontSize(12).setFontColor(FOOTBALL_GREEN).setMarginTop(6).setMarginBottom(2);
        doc.add(h2hHdr);
        if (req.getH2h()!=null) {
            AnalysisRequest.H2H h = req.getH2h();
            if (h.getInsights()!=null && !h.getInsights().isBlank()) {
                doc.add(new Paragraph(h.getInsights()).setFontSize(10).setMarginBottom(0));
            }
            doc.add(new Paragraph(String.format("GD (%s perspective): %s; Avg GD: %.2f",
                    home,
                    (h.getGoalDifferential()>=0? "+"+((int)h.getGoalDifferential()): String.valueOf((int)h.getGoalDifferential())),
                    h.getAverageGD())).setFontSize(9).setMarginTop(0));
            if (h.getLast5TeamA()!=null || h.getLast5TeamB()!=null) {
                String l5a = h.getLast5TeamA()!=null? safe(h.getLast5TeamA().getStreak()):"";
                String l5b = h.getLast5TeamB()!=null? safe(h.getLast5TeamB().getStreak()):"";
                doc.add(new Paragraph(String.format("%s Last 5: %s   |   %s Last 5: %s", home, l5a, away, l5b)).setFontSize(9));
            }
        } else {
            doc.add(new Paragraph("No H2H insights available.").setFontSize(9));
        }

        // H2H tables (limited rows for single-page)
        int histLimit = 5; int allLimit = 7;
        java.util.List<AnalysisRequest.Row> hist = (req.getH2h()!=null? req.getH2h().getHistory(): null);
        java.util.List<AnalysisRequest.Row> all = (req.getH2h()!=null? req.getH2h().getAllOrientations(): null);
        Paragraph hChosen = new Paragraph("H2H (Chosen Orientation) — last " + histLimit).setFontColor(teamAColor).setBold().setFontSize(10).setMarginTop(2).setMarginBottom(1);
        doc.add(hChosen);
        doc.add(buildH2HTableLimited(hist, histLimit, ROW_ALT).setFontSize(8));
        if (hist != null && hist.size() > histLimit) {
            doc.add(new Paragraph("... earlier matches available in app").setFontSize(8).setItalic());
        }
        Paragraph hAll = new Paragraph("H2H (All Orientations) — last " + allLimit).setFontColor(teamBColor).setBold().setFontSize(10).setMarginTop(2).setMarginBottom(1);
        doc.add(hAll);
        doc.add(buildH2HTableLimited(all, allLimit, ROW_ALT).setFontSize(8));
        if (all != null && all.size() > allLimit) {
            doc.add(new Paragraph("... earlier matches available in app").setFontSize(8).setItalic());
        }

        // Predictions table with progress bars
        Paragraph predHdr = new Paragraph("Predictions").setBold().setFontSize(12).setFontColor(TEAM_RED).setMarginTop(6).setMarginBottom(2);
        doc.add(predHdr);
        doc.add(buildResultsTableWithBars(home, away, req.getPredictions(), FOOTBALL_GREEN, TEAM_RED));

        // Streak insight summary (if available) — render table + narration with colored highlights
        if (req.getH2h()!=null && ((req.getH2h().getStreakInsightA()!=null && !req.getH2h().getStreakInsightA().isBlank()) || (req.getH2h().getStreakInsightB()!=null && !req.getH2h().getStreakInsightB().isBlank()))) {
            Paragraph streakHdr = new Paragraph("Streak Insight Summary").setBold().setFontSize(12).setFontColor(TEAM_BLUE).setMarginTop(6).setMarginBottom(2);
            doc.add(streakHdr);
            // Team A
            if (req.getH2h().getStreakInsightA()!=null && !req.getH2h().getStreakInsightA().isBlank()) {
                StreakParsed spa = parseStreakInsight(req.getH2h().getStreakInsightA());
                String team = safe(req.getTeamA()!=null? req.getTeamA().getName(): "Team A");
                doc.add(new Paragraph(team + " current streak").setFontSize(10).setBold().setFontColor(new DeviceRgb(159,179,205)).setMarginTop(2).setMarginBottom(2));
                doc.add(buildStreakTable(spa));
                doc.add(buildStreakNarrationColored(spa, team));
            }
            // Team B
            if (req.getH2h().getStreakInsightB()!=null && !req.getH2h().getStreakInsightB().isBlank()) {
                StreakParsed spb = parseStreakInsight(req.getH2h().getStreakInsightB());
                String team = safe(req.getTeamB()!=null? req.getTeamB().getName(): "Team B");
                doc.add(new Paragraph(team + " current streak").setFontSize(10).setBold().setFontColor(new DeviceRgb(159,179,205)).setMarginTop(6).setMarginBottom(2));
                doc.add(buildStreakTable(spb));
                doc.add(buildStreakNarrationColored(spb, team));
            }
        }

        // Correct scores (top 5)
        Paragraph csHdr = new Paragraph("Top Correct Scores").setBold().setFontSize(12).setFontColor(TEAM_BLUE).setMarginTop(4).setMarginBottom(2);
        doc.add(csHdr);
        doc.add(buildScoresTableLimited(req.getPredictions()!=null? req.getPredictions().getCorrectScores(): null, 5));

        // Footer
        doc.add(new Paragraph("Generated by ChambuaVismart | Single-page view optimized").setFontSize(8).setTextAlignment(TextAlignment.CENTER).setMarginTop(4));

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

    // Build limited H2H table with alternating row color
    private Table buildH2HTableLimited(java.util.List<AnalysisRequest.Row> rows, int limit, DeviceRgb alt) {
        Table t = new Table(new float[]{1.0f, 1.6f, 5.2f, 1.4f});
        t.setWidth(UnitValue.createPercentValue(100));
        t.addHeaderCell(new Cell().add(new Paragraph("Year").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Date").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Match").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Result").setBold()));
        if (rows == null || rows.isEmpty()) {
            t.addCell(new Cell(1,4).add(new Paragraph("No matches found")).setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
            return t;
        }
        int size = rows.size();
        int from = Math.max(0, size - limit);
        boolean altOn = false;
        for (int i = from; i < size; i++) {
            AnalysisRequest.Row r = rows.get(i);
            Cell c1 = new Cell().add(new Paragraph(r.getYear()!=null? r.getYear().toString(): ""));
            Cell c2 = new Cell().add(new Paragraph(safe(r.getDate())));
            Cell c3 = new Cell().add(new Paragraph(safe(r.getMatch())));
            Cell c4 = new Cell().add(new Paragraph(safe(r.getResult())));
            if (altOn) { c1.setBackgroundColor(alt); c2.setBackgroundColor(alt); c3.setBackgroundColor(alt); c4.setBackgroundColor(alt);}            
            c1.setBorder(new SolidBorder(ColorConstants.GRAY, 0.3f));
            c2.setBorder(new SolidBorder(ColorConstants.GRAY, 0.3f));
            c3.setBorder(new SolidBorder(ColorConstants.GRAY, 0.3f));
            c4.setBorder(new SolidBorder(ColorConstants.GRAY, 0.3f));
            t.addCell(c1); t.addCell(c2); t.addCell(c3); t.addCell(c4);
            altOn = !altOn;
        }
        return t;
    }

    // Results with progress bars
    private Table buildResultsTableWithBars(String home, String away, AnalysisRequest.Predictions p, DeviceRgb green, DeviceRgb red) {
        Table t = new Table(new float[]{3.6f, 3.4f});
        t.setWidth(UnitValue.createPercentValue(100));
        t.addHeaderCell(new Cell().add(new Paragraph("Outcome").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Probability").setBold()));
        if (p == null) {
            t.addCell(new Cell(1,2).add(new Paragraph("No predictions available")));
            return t;
        }
        addOutcomeRow(t, home + " Win", p.getWin(), green);
        addOutcomeRow(t, "Draw", p.getDraw(), green);
        addOutcomeRow(t, away + " Win", p.getLoss(), red);
        addOutcomeRow(t, "BTTS", p.getBtts(), green);
        addOutcomeRow(t, "Over 1.5", p.getOver15(), green);
        addOutcomeRow(t, "Over 2.5", p.getOver25(), green);
        addOutcomeRow(t, "Over 3.5", p.getOver35(), green);
        t.setFontSize(9);
        return t;
    }

    private void addOutcomeRow(Table t, String label, int percent, DeviceRgb color) {
        t.addCell(new Cell().add(new Paragraph(label)));
        Paragraph pct = new Paragraph(percent + "%").setFontSize(9).setTextAlignment(TextAlignment.RIGHT);
        Cell barCell = new Cell().add(pct);
        barCell.setPaddingLeft(4).setPaddingRight(4);
        barCell.setNextRenderer(new BarCellRenderer(barCell, percent, color));
        t.addCell(barCell);
    }

    // Limited scores table
    private Table buildScoresTableLimited(java.util.List<AnalysisRequest.Score> list, int limit) {
        Table t = new Table(new float[]{2.0f, 1.2f});
        t.setWidth(UnitValue.createPercentValue(60));
        t.addHeaderCell(new Cell().add(new Paragraph("Score").setBold()));
        t.addHeaderCell(new Cell().add(new Paragraph("Probability").setBold()));
        if (list == null || list.isEmpty()) {
            t.addCell(new Cell(1,2).add(new Paragraph("No scores available")));
            return t;
        }
        int lim = Math.min(limit, list.size());
        for (int i=0; i<lim; i++) {
            AnalysisRequest.Score s = list.get(i);
            t.addCell(new Cell().add(new Paragraph(safe(s.getScore()))));
            t.addCell(new Cell().add(new Paragraph(String.format(java.util.Locale.US, "%.1f%%", s.getProbability()))));
        }
        t.setFontSize(9);
        return t;
    }

    // Renderer to draw a progress bar behind the probability text
    private static class BarCellRenderer extends CellRenderer {
        private final int percent;
        private final DeviceRgb color;
        BarCellRenderer(Cell modelElement, int percent, DeviceRgb color) { super(modelElement); this.percent = Math.max(0, Math.min(100, percent)); this.color = color; }
        @Override
        public void draw(DrawContext drawContext) {
            Rectangle rect = getOccupiedAreaBBox();
            float pad = 2f;
            float reservedRight = 34f; // space reserved for percentage text
            float x = rect.getLeft() + pad;
            // place bar towards the bottom of the cell to avoid text overlap
            float y = rect.getBottom() + 1.5f;
            float wFull = Math.max(0, rect.getWidth() - 2*pad);
            float w = Math.max(0, wFull - reservedRight);
            float h = 4.0f; // slightly thinner
            PdfCanvas canvas = drawContext.getCanvas();
            // draw bar first (behind), then text via super.draw (aligned right)
            canvas.saveState();
            canvas.setFillColor(new DeviceRgb(230,230,230));
            canvas.rectangle(x, y, w, h);
            canvas.fill();
            float wf = w * (percent / 100f);
            canvas.setFillColor(color);
            canvas.rectangle(x, y, wf, h);
            canvas.fill();
            canvas.restoreState();
            // now draw the cell's content (percentage text) on top
            super.draw(drawContext);
        }
    }
    
    // --- Streak Insight helpers (PDF fallback rendering) ---
    private static class StreakParsed {
        String pattern;
        int instances;
        int win;
        int draw;
        int loss;
        int over15;
        int over25;
        int over35;
        int btts;
    }

    private StreakParsed parseStreakInsight(String text) {
        StreakParsed sp = new StreakParsed();
        if (text == null) return sp;
        try {
            java.util.regex.Pattern pInst = java.util.regex.Pattern.compile("has had\\s+(\\d+)\\s+instances? of a\\s+([0-9]+[WDL])", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m1 = pInst.matcher(text);
            if (m1.find()) {
                sp.instances = Integer.parseInt(m1.group(1));
                sp.pattern = m1.group(2);
            }
            java.util.regex.Pattern pW = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+wins", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pD = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+draws", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pL = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+losses", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pO35 = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+Over\\s+3\\.5", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pO25 = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+Over\\s+2\\.5", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pO15 = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+Over\\s+1\\.5", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pBT = java.util.regex.Pattern.compile("(\\d+)\\%\\s+were\\s+BTTS", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher mw = pW.matcher(text); if (mw.find()) sp.win = parseIntSafe(mw.group(1));
            java.util.regex.Matcher md = pD.matcher(text); if (md.find()) sp.draw = parseIntSafe(md.group(1));
            java.util.regex.Matcher ml = pL.matcher(text); if (ml.find()) sp.loss = parseIntSafe(ml.group(1));
            java.util.regex.Matcher m35 = pO35.matcher(text); if (m35.find()) sp.over35 = parseIntSafe(m35.group(1));
            java.util.regex.Matcher m25 = pO25.matcher(text); if (m25.find()) sp.over25 = parseIntSafe(m25.group(1));
            java.util.regex.Matcher m15 = pO15.matcher(text); if (m15.find()) sp.over15 = parseIntSafe(m15.group(1));
            java.util.regex.Matcher mb = pBT.matcher(text); if (mb.find()) sp.btts = parseIntSafe(mb.group(1));
        } catch (Exception ignored) {}
        return sp;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private String fmtPct(int v) {
        int n = Math.max(0, Math.min(100, v));
        return String.valueOf(n);
    }

    private Table buildStreakTable(StreakParsed sp) {
        Table t = new Table(new float[]{3.6f, 2.0f});
        t.setWidth(UnitValue.createPercentValue(60));
        t.addHeaderCell(new Cell().add(new Paragraph("Metric").setBold()))
         .addHeaderCell(new Cell().add(new Paragraph("Value").setBold()));
        t.addCell(new Cell().add(new Paragraph("Instances")));
        t.addCell(new Cell().add(new Paragraph(String.valueOf(sp.instances))));
        t.addCell(new Cell().add(new Paragraph("Next Match: Win / Draw / Loss")));
        t.addCell(new Cell().add(new Paragraph(fmtPct(sp.win) + "% / " + fmtPct(sp.draw) + "% / " + fmtPct(sp.loss) + "%")));
        t.addCell(new Cell().add(new Paragraph("Over 3.5 / Over 2.5 / Over 1.5")));
        t.addCell(new Cell().add(new Paragraph(fmtPct(sp.over35) + "% / " + fmtPct(sp.over25) + "% / " + fmtPct(sp.over15) + "%")));
        t.addCell(new Cell().add(new Paragraph("BTTS")));
        t.addCell(new Cell().add(new Paragraph(fmtPct(sp.btts) + "%")));
        t.setFontSize(9);
        return t;
    }

    private Paragraph buildStreakNarrationColored(StreakParsed sp, String team) {
        // Helper to color based on >70% and metric semantics
        java.util.function.BiFunction<Integer, String, Text> colored = (val, kind) -> {
            int v = Math.max(0, Math.min(100, val));
            Text txt = new Text(v + "%");
            boolean high = v > 70;
            if (high) {
                if ("loss".equals(kind)) txt.setFontColor(new DeviceRgb(239,68,68)).setBold(); // red
                else txt.setFontColor(new DeviceRgb(16,185,129)).setBold(); // green for win/overs/btts
            }
            return txt;
        };
        Paragraph p = new Paragraph().setFontSize(9).setMarginTop(4);
        p.add(new Text(team + " has had " + sp.instances + " instances of a " + (sp.pattern!=null? sp.pattern: "streak") + " streak. Of the matches that followed: "));
        p.add(colored.apply(sp.win, "win")).add(new Text(" were wins, "));
        p.add(colored.apply(sp.draw, "draw")).add(new Text(" were draws, "));
        p.add(colored.apply(sp.loss, "loss")).add(new Text(" were losses. "));
        p.add(colored.apply(sp.over35, "over")).add(new Text(" were Over 3.5, "));
        p.add(colored.apply(sp.over25, "over")).add(new Text(" were Over 2.5, "));
        p.add(colored.apply(sp.over15, "over")).add(new Text(" were Over 1.5, and "));
        p.add(colored.apply(sp.btts, "btts")).add(new Text(" were BTTS."));
        return p;
    }
}
