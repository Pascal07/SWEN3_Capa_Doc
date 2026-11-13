package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOcrIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void extractText_withRealGhostscriptAndTesseract_extractsHelloWorld() throws Exception {
        ProcessCommandRunner runner = new ProcessCommandRunner();

        Assumptions.assumeTrue(isBinaryAvailable(runner, "gs"), "Ghostscript (gs) is not installed locally");
        Assumptions.assumeTrue(isBinaryAvailable(runner, "tesseract"), "Tesseract is not installed locally");

        String language = resolveAvailableLanguage(runner);
        Assumptions.assumeTrue(language != null, "No suitable Tesseract language pack installed locally");

        OcrProperties properties = new OcrProperties();
        properties.setLanguages(language);
        properties.setDpi(300);
        properties.setTimeoutPerPage(Duration.ofSeconds(30));
        properties.setTesseractCommand("tesseract");
        properties.setGhostscriptCommand("gs");
        properties.setTempDir(tempDir.toString());

        TesseractOcrService ocrService = new TesseractOcrService(properties, runner);
        byte[] helloWorldPdf = createMinimalHelloWorldPdf();

        String extractedText = ocrService.extractText(helloWorldPdf);

        assertThat(extractedText).containsIgnoringCase("Hello");
    }

    private boolean isBinaryAvailable(ProcessCommandRunner runner, String binary) {
        try {
            CommandRunner.CommandResult res = runner.run(List.of(binary, "--version"), tempDir, Duration.ofSeconds(5));
            return !res.timedOut() && res.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveAvailableLanguage(ProcessCommandRunner runner) {
        try {
            CommandRunner.CommandResult res = runner.run(List.of("tesseract", "--list-langs"), tempDir, Duration.ofSeconds(5));
            if (res.timedOut() || res.exitCode() != 0) {
                return null;
            }
            String combined = res.stdout() + "\n" + res.stderr();
            Set<String> langs = Arrays.stream(combined.split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("List of available languages"))
                    .collect(Collectors.toSet());

            if (langs.contains("deu") && langs.contains("eng")) {
                return "deu+eng";
            }
            if (langs.contains("eng")) {
                return "eng";
            }
            if (langs.contains("deu")) {
                return "deu";
            }
            // Fallback to any installed Latin script language (e.g. afr) for host environments
            for (String lang : langs) {
                if (!"osd".equals(lang) && !"equ".equals(lang)) {
                    return lang;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generates a valid single-page PDF containing "Hello World" rendered in 36pt Helvetica.
     */
    static byte[] createMinimalHelloWorldPdf() {
        String pdf = """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                2 0 obj
                << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                3 0 obj
                << /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >>
                endobj
                4 0 obj
                << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
                endobj
                5 0 obj
                << /Length 48 >>
                stream
                BT
                /F1 36 Tf
                100 700 Td
                (Hello World) Tj
                ET
                endstream
                endobj
                xref
                0 6
                0000000000 65535 f 
                0000000009 00000 n 
                0000000058 00000 n 
                0000000115 00000 n 
                0000000229 00000 n 
                0000000297 00000 n 
                trailer
                << /Size 6 /Root 1 0 R >>
                startxref
                395
                %%EOF
                """;
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }
}
