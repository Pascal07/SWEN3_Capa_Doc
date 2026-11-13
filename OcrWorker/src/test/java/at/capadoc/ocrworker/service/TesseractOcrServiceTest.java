package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TesseractOcrServiceTest {

    @TempDir
    Path baseTempDir;

    private OcrProperties ocrProperties;

    @BeforeEach
    void setUp() {
        ocrProperties = new OcrProperties();
        ocrProperties.setLanguages("deu+eng");
        ocrProperties.setDpi(300);
        ocrProperties.setTimeoutPerPage(Duration.ofSeconds(10));
        ocrProperties.setTesseractCommand("tesseract");
        ocrProperties.setGhostscriptCommand("gs");
        ocrProperties.setTempDir(baseTempDir.toString());
        ocrProperties.setMaxPages(50);
        ocrProperties.setMaxPdfSizeBytes(10 * 1024 * 1024L);
    }

    private byte[] validPdfBytes() {
        return "%PDF-1.4\n1 0 obj <<>> endobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void extractText_singlePage_successAndCleansUpTempDir() {
        AtomicReference<Path> capturedWorkDir = new AtomicReference<>();

        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            capturedWorkDir.set(workingDir);
            String executable = command.getFirst();
            if ("gs".equals(executable)) {
                writeFile(workingDir.resolve("page-001.png"), "fake-png-1");
                return new CommandRunner.CommandResult(0, "", "", false);
            } else if ("tesseract".equals(executable)) {
                assertThat(command).contains("stdout", "-l", "deu+eng");
                return new CommandRunner.CommandResult(0, "Hallo Welt\n", "", false);
            }
            throw new AssertionError("Unexpected command: " + command);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);
        String result = service.extractText(validPdfBytes());

        assertThat(result).isEqualTo("Hallo Welt");
        assertThat(capturedWorkDir.get()).isNotNull();
        assertThat(Files.exists(capturedWorkDir.get())).isFalse();
    }

    @Test
    void extractText_multiplePages_maintainsCorrectPageOrder() {
        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            String executable = command.getFirst();
            if ("gs".equals(executable)) {
                // Create out of order to ensure lexicographical sorting works
                writeFile(workingDir.resolve("page-003.png"), "png3");
                writeFile(workingDir.resolve("page-001.png"), "png1");
                writeFile(workingDir.resolve("page-002.png"), "png2");
                return new CommandRunner.CommandResult(0, "", "", false);
            } else if ("tesseract".equals(executable)) {
                String inputPath = command.get(1);
                if (inputPath.endsWith("page-001.png")) {
                    return new CommandRunner.CommandResult(0, "Seite 1 Text", "", false);
                } else if (inputPath.endsWith("page-002.png")) {
                    return new CommandRunner.CommandResult(0, "Seite 2 Text", "", false);
                } else if (inputPath.endsWith("page-003.png")) {
                    return new CommandRunner.CommandResult(0, "Seite 3 Text", "", false);
                }
            }
            throw new AssertionError("Unexpected command: " + command);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);
        String result = service.extractText(validPdfBytes());

        assertThat(result).isEqualTo("Seite 1 Text\n\nSeite 2 Text\n\nSeite 3 Text");
    }

    @Test
    void extractText_emptyPage_returnsEmptyStringWithoutError() {
        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            String executable = command.getFirst();
            if ("gs".equals(executable)) {
                writeFile(workingDir.resolve("page-001.png"), "blank-png");
                return new CommandRunner.CommandResult(0, "", "", false);
            } else if ("tesseract".equals(executable)) {
                return new CommandRunner.CommandResult(0, "   \n  ", "", false);
            }
            throw new AssertionError("Unexpected command: " + command);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);
        String result = service.extractText(validPdfBytes());

        assertThat(result).isEmpty();
    }

    @Test
    void extractText_ghostscriptNonZeroExitCode_throwsPermanentExceptionAndCleansTempDir() {
        AtomicReference<Path> capturedWorkDir = new AtomicReference<>();

        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            capturedWorkDir.set(workingDir);
            return new CommandRunner.CommandResult(1, "", "Error: /invalidfileaccess", false);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);

        assertThatThrownBy(() -> service.extractText(validPdfBytes()))
                .isInstanceOf(OcrPermanentException.class)
                .hasMessageContaining("Ghostscript failed with exit code 1");

        assertThat(capturedWorkDir.get()).isNotNull();
        assertThat(Files.exists(capturedWorkDir.get())).isFalse();
    }

    @Test
    void extractText_tesseractNonZeroExitCode_throwsPermanentExceptionAndCleansTempDir() {
        AtomicReference<Path> capturedWorkDir = new AtomicReference<>();

        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            capturedWorkDir.set(workingDir);
            if ("gs".equals(command.getFirst())) {
                writeFile(workingDir.resolve("page-001.png"), "png1");
                return new CommandRunner.CommandResult(0, "", "", false);
            }
            return new CommandRunner.CommandResult(2, "", "Failed loading language", false);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);

        assertThatThrownBy(() -> service.extractText(validPdfBytes()))
                .isInstanceOf(OcrPermanentException.class)
                .hasMessageContaining("Tesseract failed on page 1 with exit code 2");

        assertThat(capturedWorkDir.get()).isNotNull();
        assertThat(Files.exists(capturedWorkDir.get())).isFalse();
    }

    @Test
    void extractText_timeoutInTesseract_throwsTransientExceptionAndCleansTempDir() {
        AtomicReference<Path> capturedWorkDir = new AtomicReference<>();

        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            capturedWorkDir.set(workingDir);
            if ("gs".equals(command.getFirst())) {
                writeFile(workingDir.resolve("page-001.png"), "png1");
                return new CommandRunner.CommandResult(0, "", "", false);
            }
            return new CommandRunner.CommandResult(-1, "", "Timed out", true);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);

        assertThatThrownBy(() -> service.extractText(validPdfBytes()))
                .isInstanceOf(OcrTransientException.class)
                .hasMessageContaining("Tesseract timed out");

        assertThat(capturedWorkDir.get()).isNotNull();
        assertThat(Files.exists(capturedWorkDir.get())).isFalse();
    }

    @Test
    void extractText_exceedsMaxPages_throwsPermanentExceptionAndCleansTempDir() {
        ocrProperties.setMaxPages(2);
        AtomicReference<Path> capturedWorkDir = new AtomicReference<>();

        CommandRunner mockRunner = (command, workingDir, timeout) -> {
            capturedWorkDir.set(workingDir);
            writeFile(workingDir.resolve("page-001.png"), "png1");
            writeFile(workingDir.resolve("page-002.png"), "png2");
            writeFile(workingDir.resolve("page-003.png"), "png3");
            return new CommandRunner.CommandResult(0, "", "", false);
        };

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);

        assertThatThrownBy(() -> service.extractText(validPdfBytes()))
                .isInstanceOf(OcrPermanentException.class)
                .hasMessageContaining("exceeds maximum allowed pages (2)");

        assertThat(capturedWorkDir.get()).isNotNull();
        assertThat(Files.exists(capturedWorkDir.get())).isFalse();
    }

    @Test
    void extractText_invalidPdfHeader_throwsPermanentException() {
        CommandRunner mockRunner = (command, workingDir, timeout) ->
                new CommandRunner.CommandResult(0, "", "", false);

        TesseractOcrService service = new TesseractOcrService(ocrProperties, mockRunner);
        byte[] notAPdf = "This is plain text, not a PDF".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.extractText(notAPdf))
                .isInstanceOf(OcrPermanentException.class)
                .hasMessageContaining("missing %PDF magic bytes");
    }
}
