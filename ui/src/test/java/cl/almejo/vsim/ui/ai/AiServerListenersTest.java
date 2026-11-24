package cl.almejo.vsim.ui.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiServerListenersTest {

    @Test
    void onStateChangedRunsOnlyWhenTheStateChanges() {
        int[] runs = {0};
        AiServerService.Listener listener = AiServerService.Listener.onStateChanged(() -> runs[0]++);

        listener.requestServed();
        listener.questionUnattended("task", "question", Duration.ZERO);
        assertThat(runs[0]).isZero();
        listener.stateChanged();
        assertThat(runs[0]).isEqualTo(1);
    }
}
