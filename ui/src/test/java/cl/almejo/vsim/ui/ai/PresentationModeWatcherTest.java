package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class PresentationModeWatcherTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<OpenDocument> open = new ArrayList<>();
    private final Map<OpenDocument, DocumentId> ids = new HashMap<>();
    private final List<String> told = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor background = queued::add;
    private final PresentationModeWatcher watcher = new PresentationModeWatcher(() -> List.copyOf(open),
            document -> PresentationPreferences.read(preferences), ids::get, new PresentationModeWatcher.Target() {
                @Override
                public void presentationChanged(DocumentId document, PresentationSettings settings) {
                    told.add(document.value() + " " + settings.presenting() + " " + settings.pace());
                }

                @Override
                public void documentClosed(DocumentId document) {
                    told.add(document.value() + " closed");
                }
            }, background);

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    @Test
    void aModeChangeIsToldOffTheCallersThreadAndOnlyOnce() {
        open("circuit-1");
        watcher.refresh();
        assertThat(queued).isEmpty();

        preferences.setAiPresenting(true);
        preferences.setAiPace(PresentationPace.CONTINUOUS);
        watcher.refresh();
        watcher.refresh();

        assertThat(told).isEmpty();
        runQueued();
        assertThat(told).containsExactly("circuit-1 true CONTINUOUS");
    }

    @Test
    void aClosedCircuitIsToldOnce() {
        OpenDocument circuit = open("circuit-1");
        watcher.refresh();

        open.remove(circuit);
        circuit.close();
        watcher.refresh();
        watcher.refresh();
        runQueued();

        assertThat(told).containsExactly("circuit-1 closed");
    }

    @Test
    void theWindowsPreferencesGiveThePaceAndPause() {
        preferences.setAiPresenting(true);
        preferences.setAiPauseMillis(2500);

        assertThat(PresentationPreferences.read(preferences)).isEqualTo(new PresentationSettings(true,
                PresentationPace.STEP_BY_STEP, Duration.ofMillis(2500)));
    }

    private OpenDocument open(String id) {
        OpenDocument circuit = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null, false);
        open.add(circuit);
        ids.put(circuit, new DocumentId(id));
        return circuit;
    }

    private void runQueued() {
        List<Runnable> work = List.copyOf(queued);
        queued.clear();
        work.forEach(Runnable::run);
    }
}
