package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeardownTest {

    @Test
    void stepsAreUndoneLastFirstOnceAndAStepAddedMeanwhileWaitsForTheNextRun() {
        List<String> undone = new ArrayList<>();
        Teardown teardown = new Teardown();
        teardown.add(() -> undone.add("first"));
        teardown.add(() -> {
            undone.add("second");
            teardown.add(() -> undone.add("late"));
        });

        teardown.run();
        assertThat(undone).containsExactly("second", "first");

        teardown.run();
        assertThat(undone).containsExactly("second", "first", "late");
    }

    @Test
    void everyStepRunsEvenWhenOneFailsAndTheFirstFailureIsThrown() {
        List<String> undone = new ArrayList<>();
        Teardown teardown = new Teardown();
        teardown.add(() -> undone.add("first"));
        teardown.add(() -> {
            throw new IllegalStateException("second failed");
        });
        teardown.add(() -> undone.add("third"));

        assertThatThrownBy(teardown::run).isInstanceOf(IllegalStateException.class).hasMessage("second failed");
        assertThat(undone).containsExactly("third", "first");

        teardown.run();
        assertThat(undone).containsExactly("third", "first");
    }
}
