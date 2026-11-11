package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void changingTheSpeedDoesNotModifyTheFileButIsSavedWithIt() throws Exception {
        OpenDocument document = open("");
        try {
            document.setSpeed(10.0);
            waitUntil(() -> document.engineSpeed() == 10.0);
            assertThat(document.isModified()).isFalse();
            assertThat(document.serialized()).contains("\"simulationSpeed\" : 10");
        } finally {
            document.close();
        }
    }
}
