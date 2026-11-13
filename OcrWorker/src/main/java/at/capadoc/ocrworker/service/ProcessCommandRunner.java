package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ProcessCommandRunner implements CommandRunner {

    @Override
    public CommandResult run(List<String> command, Path workingDir, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command list must not be null or empty");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile());
        }

        final Process process;
        try {
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new OcrPermanentException(
                    "Failed to start external command '" + command.getFirst() + "' (binary not installed or not executable)",
                    ex
            );
        }

        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                () -> readStreamSafely(process.getInputStream())
        );
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                () -> readStreamSafely(process.getErrorStream())
        );

        long timeoutMillis = (timeout != null && timeout.toMillis() > 0) ? timeout.toMillis() : 30_000L;
        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                String partialOut = getFutureValue(stdoutFuture);
                String partialErr = getFutureValue(stderrFuture);
                return new CommandResult(-1, partialOut, partialErr, true);
            }

            int exitCode = process.exitValue();
            String stdout = getFutureValue(stdoutFuture);
            String stderr = getFutureValue(stderrFuture);
            return new CommandResult(exitCode, stdout, stderr, false);
        } catch (InterruptedException ie) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new OcrTransientException("Interrupted while waiting for command '" + command.getFirst() + "'", ie);
        }
    }

    private String readStreamSafely(InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private String getFutureValue(CompletableFuture<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException ex) {
            return "";
        }
    }
}
