package cl.almejo.vsim.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StepsTest {

    @Test
    void everyStepRunsAndTheFirstFailureComesBackWithTheLaterOnesSuppressed() {
        List<String> ran = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");

        Optional<RuntimeException> failure = Steps.runEach(List.of(
                () -> {
                    ran.add("one");
                    throw first;
                },
                () -> ran.add("two"),
                () -> {
                    ran.add("three");
                    throw second;
                }));

        assertThat(ran).containsExactly("one", "two", "three");
        assertThat(failure).containsSame(first);
        assertThat(first.getSuppressed()).containsExactly(second);
        assertThat(Steps.runEach(List.of(() -> ran.add("four")))).isEmpty();
    }
}
