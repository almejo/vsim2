package cl.almejo.vsim.ui.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Starts the command as a real process; a command that is missing, fails or exceeds the timeout gives empty. */
public final class ProcessCommandRunner implements CommandRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCommandRunner.class);

    private final Duration timeout;

    public ProcessCommandRunner(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public Optional<String> run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                LOGGER.debug("{} did not finish within {}", command, timeout);
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
        } catch (IOException exception) {
            LOGGER.debug("Could not run {}", command, exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
