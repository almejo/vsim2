package cl.almejo.vsim.ui.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

class DaemonThreadsTest {

    @Test
    void theThreadIsADaemonWithTheGivenName() throws Exception {
        ExecutorService executor = DaemonThreads.single("vsim-test-thread");
        try {
            Thread thread = executor.submit(Thread::currentThread).get();
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).isEqualTo("vsim-test-thread");
        } finally {
            executor.shutdownNow();
        }
    }
}
