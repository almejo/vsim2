package cl.almejo.vsim.core.tools;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SimulateCommandLineTest {

    private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
    private final PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

    private String output() {
        return outputBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsTheTruthTableOfTheVsimFullAdder() {
        int exitCode = SimulateCommandLine.run(new String[]{"src/test/resources/vsim1/4-bit-adder.json"}, out, error);
        assertThat(exitCode).isZero();
        assertThat(output().lines().toList()).containsExactly(
                "a0 b0 c | xor2:c2 or2:c5",
                "0 0 0 | 0 0",
                "0 0 1 | 1 0",
                "0 1 0 | 1 0",
                "0 1 1 | 0 1",
                "1 0 0 | 1 0",
                "1 0 1 | 0 1",
                "1 1 0 | 0 1",
                "1 1 1 | 1 1");
    }

    @Test
    void missingArgumentsPrintUsage() {
        assertThat(SimulateCommandLine.run(new String[]{}, out, error)).isEqualTo(2);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).contains("usage");
    }

    @Test
    void unreadableFileIsReported() {
        assertThat(SimulateCommandLine.run(new String[]{"does-not-exist.vsim"}, out, error)).isEqualTo(1);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).startsWith("error: READ_ERROR");
    }
}
