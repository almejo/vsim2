package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class OpenDocumentSpeedTest {

    @TempDir
    Path folder;

    private OpenDocument open(String extraKeys) throws IOException, CircuitFormatException {
        Path file = folder.resolve("circuit.vsim");
        Files.writeString(file, "{\"format\":\"vsim2\",\"formatVersion\":2,\"kind\":\"circuit\"" + extraKeys + "}");
        return OpenDocument.open(file, StandardComponentTypes.registry());
    }

    @Test
    void aStoredSpeedAppliesAtOnceOnOpening() throws Exception {
        OpenDocument document = open(",\"simulationSpeed\":1000");
        try {
            waitUntil(() -> document.engineSpeed() == 1000.0);
            assertThat(document.simulationSpeed()).isEqualTo(1000.0);
            assertThat(document.isModified()).isFalse();
        } finally {
            document.close();
        }
    }

    @Test
    void aFileWithoutTheKeyOpensAtTheDefaultSpeed() throws Exception {
        OpenDocument document = open("");
        try {
            assertThat(document.simulationSpeed()).isEqualTo(1.0);
            assertThat(document.engineSpeed()).isEqualTo(1.0);
        } finally {
            document.close();
        }
    }

    @Test
    void anInvalidStoredSpeedOpensAtTheDefaultSpeed() throws Exception {
        OpenDocument document = open(",\"simulationSpeed\":\"fast\"");
        try {
            assertThat(document.simulationSpeed()).isEqualTo(1.0);
            assertThat(document.engineSpeed()).isEqualTo(1.0);
        } finally {
            document.close();
        }
    }

    @Test
    void changingTheSpeedModifiesTheCircuitAndIsSavedWithIt() throws Exception {
        OpenDocument document = open("");
        try {
            assertThat(document.isModified()).isFalse();
            document.setSpeed(10.0);
            waitUntil(() -> document.engineSpeed() == 10.0);
            assertThat(document.isModified()).isTrue();
            assertThat(document.serialized()).contains("\"simulationSpeed\" : 10");
        } finally {
            document.close();
        }
    }

    @Test
    void undoAndRedoRestoreTheSpeedInTheEngineAndTellTheSelector() throws Exception {
        OpenDocument document = open(",\"simulationSpeed\":100");
        try {
            AtomicInteger notifications = new AtomicInteger();
            document.addSpeedListener(notifications::incrementAndGet);
            document.setSpeed(1000.0);
            waitUntil(() -> document.engineSpeed() == 1000.0);
            assertThat(notifications).hasValue(1);

            document.history().undo();
            assertThat(document.simulationSpeed()).isEqualTo(100.0);
            waitUntil(() -> document.engineSpeed() == 100.0);
            assertThat(document.isModified()).isFalse();
            assertThat(notifications).hasValue(2);

            document.history().redo();
            assertThat(document.simulationSpeed()).isEqualTo(1000.0);
            waitUntil(() -> document.engineSpeed() == 1000.0);
            assertThat(notifications).hasValue(3);
        } finally {
            document.close();
        }
    }

    @Test
    void choosingTheSameSpeedAgainDoesNothing() throws Exception {
        OpenDocument document = open(",\"simulationSpeed\":100");
        try {
            document.setSpeed(100.0);
            assertThat(document.isModified()).isFalse();
            assertThat(document.history().canUndo()).isFalse();
        } finally {
            document.close();
        }
    }
}
