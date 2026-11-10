package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TesseractOcrService implements OcrService {

    private static final byte[] PDF_MAGIC_BYTES = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final OcrProperties ocrProperties;
    private final CommandRunner commandRunner;

    @Override
    public String extractText(byte[] pdf) {
        validatePdfBytes(pdf);

        Path tempDir = createFreshTempDirectory();
        try {
            Path inputPdf = tempDir.resolve("input.pdf");
            Files.write(inputPdf, pdf);

            runGhostscript(tempDir, inputPdf);

            List<Path> pageImages = findRenderedPageImages(tempDir);
            if (pageImages.isEmpty()) {
                throw new OcrPermanentException("Ghostscript produced no page images for the input PDF");
            }
            if (ocrProperties.getMaxPages() > 0 && pageImages.size() > ocrProperties.getMaxPages()) {
                throw new OcrPermanentException(
                        "PDF page count (" + pageImages.size() + ") exceeds maximum allowed pages ("
                                + ocrProperties.getMaxPages() + ")"
                );
            }

            List<String> pageTexts = new ArrayList<>(pageImages.size());
            for (int i = 0; i < pageImages.size(); i++) {
                Path pageImage = pageImages.get(i);
                String pageText = runTesseractForPage(tempDir, pageImage, i + 1);
                if (pageText.isBlank()) {
                    log.info("OCR page {} ({}) produced empty text", i + 1, pageImage.getFileName());
                } else {
                    pageTexts.add(pageText.strip());
                }
            }

            return String.join("\n\n", pageTexts);
        } catch (IOException ex) {
            throw new OcrTransientException("Temporary file I/O error during OCR processing", ex);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    private void validatePdfBytes(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new OcrPermanentException("PDF byte array must not be null or empty");
        }
        if (ocrProperties.getMaxPdfSizeBytes() > 0 && pdf.length > ocrProperties.getMaxPdfSizeBytes()) {
            throw new OcrPermanentException(
                    "PDF size (" + pdf.length + " bytes) exceeds maximum allowed size ("
                            + ocrProperties.getMaxPdfSizeBytes() + " bytes)"
            );
        }
        if (pdf.length < PDF_MAGIC_BYTES.length) {
            throw new OcrPermanentException("Invalid PDF content: missing %PDF magic bytes");
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (pdf[i] != PDF_MAGIC_BYTES[i]) {
                throw new OcrPermanentException("Invalid PDF content: missing %PDF magic bytes");
            }
        }
    }

    private Path createFreshTempDirectory() {
        try {
            String configuredBase = ocrProperties.getTempDir();
            if (configuredBase != null && !configuredBase.isBlank()) {
                Path basePath = Path.of(configuredBase);
                Files.createDirectories(basePath);
                return Files.createTempDirectory(basePath, "capadoc-ocr-");
            }
            return Files.createTempDirectory("capadoc-ocr-");
        } catch (IOException ex) {
            throw new OcrTransientException("Failed to create temporary directory for OCR processing", ex);
        }
    }

    private void runGhostscript(Path tempDir, Path inputPdf) {
        String outputPattern = tempDir.resolve("page-%03d.png").toAbsolutePath().toString();
        List<String> gsCommand = List.of(
                ocrProperties.getGhostscriptCommand(),
                "-dNOPAUSE",
                "-dBATCH",
                "-dSAFER",
                "-sDEVICE=png16m",
                "-r" + ocrProperties.getDpi(),
                "-sOutputFile=" + outputPattern,
                inputPdf.toAbsolutePath().toString()
        );

        CommandRunner.CommandResult result = commandRunner.run(
                gsCommand,
                tempDir,
                ocrProperties.getTimeoutPerPage()
        );

        if (result.timedOut()) {
            throw new OcrTransientException("Ghostscript timed out while converting PDF pages to images");
        }
        if (result.exitCode() != 0) {
            String details = (result.stderr() != null && !result.stderr().isBlank())
                    ? result.stderr().strip()
                    : (result.stdout() != null ? result.stdout().strip() : "");
            throw new OcrPermanentException(
                    "Ghostscript failed with exit code " + result.exitCode()
                            + (details.isEmpty() ? "" : ": " + details)
            );
        }
    }

    private List<Path> findRenderedPageImages(Path tempDir) throws IOException {
        List<Path> pages = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, "page-*.png")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    pages.add(entry);
                }
            }
        }
        pages.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return pages;
    }

    private String runTesseractForPage(Path tempDir, Path pageImage, int pageNumber) {
        List<String> tesseractCommand = List.of(
                ocrProperties.getTesseractCommand(),
                pageImage.toAbsolutePath().toString(),
                "stdout",
                "-l",
                ocrProperties.getLanguages()
        );

        CommandRunner.CommandResult result = commandRunner.run(
                tesseractCommand,
                tempDir,
                ocrProperties.getTimeoutPerPage()
        );

        if (result.timedOut()) {
            throw new OcrTransientException("Tesseract timed out on page " + pageNumber);
        }
        if (result.exitCode() != 0) {
            String details = (result.stderr() != null && !result.stderr().isBlank())
                    ? result.stderr().strip()
                    : "";
            throw new OcrPermanentException(
                    "Tesseract failed on page " + pageNumber + " with exit code " + result.exitCode()
                            + (details.isEmpty() ? "" : ": " + details)
            );
        }
        return result.stdout() != null ? result.stdout() : "";
    }

    private void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            log.warn("Failed to delete temporary file/directory: {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            log.warn("Failed to clean up temporary OCR directory: {}", dir, ex);
        }
    }
}
