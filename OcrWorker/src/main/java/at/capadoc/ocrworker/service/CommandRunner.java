package at.capadoc.ocrworker.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface CommandRunner {

    record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {}

    CommandResult run(List<String> command, Path workingDir, Duration timeout);
}
