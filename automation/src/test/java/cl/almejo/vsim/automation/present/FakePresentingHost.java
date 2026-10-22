package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A headless editor for the presenting tests: open circuits with their own history, each window's presentation
 * settings, and a pacer whose clock and timer the test moves by hand. The editor thread is the calling thread,
 * serialised by one lock so a waiting call on another thread and the test never touch a circuit at the same time.
 */
public final class FakePresentingHost implements AutomationHost {

    private static final ComponentTypeRegistry BASE_REGISTRY = StandardComponentTypes.registry();

    private final Map<DocumentId, Circuit> circuits = new ConcurrentHashMap<>();
    private final Map<DocumentId, PresentationSettings> settings = new ConcurrentHashMap<>();
    private final FakePacer pacer = new FakePacer();
    private final Object editorThread = new Object();
    private final List<GridPoint> travels = new CopyOnWriteArrayList<>();

    public Circuit open(String id) {
        Circuit circuit = new Circuit(new CircuitDocument());
        circuits.put(new DocumentId(id), circuit);
        return circuit;
    }

    public void close(String id) {
        circuits.remove(new DocumentId(id));
    }

    public void present(String id, PresentationSettings windowSettings) {
        settings.put(new DocumentId(id), windowSettings);
    }

    public FakePacer pacer() {
        return pacer;
    }

    /** Runs {@code work} as the editor thread would, never at the same time as the service's own editor work. */
    public <T> T onEditor(Supplier<T> work) {
        synchronized (editorThread) {
            return work.get();
        }
    }

    @Override
    public PresentationSettings presentationSettings(DocumentId document) {
        return settings.getOrDefault(document, PresentationSettings.WORKING);
    }

    @Override
    public void setPresenting(DocumentId document, boolean presenting) {
        PresentationSettings current = presentationSettings(document);
        settings.put(document, new PresentationSettings(presenting, current.pace(), current.pause()));
    }

    @Override
    public void layerPresentation(DocumentId document, Optional<PresentationPace> pace, Optional<Duration> pause) {
        PresentationSettings current = presentationSettings(document);
        settings.put(document, new PresentationSettings(current.presenting(), pace.orElse(current.pace()),
                pause.orElse(current.pause())));
    }

    @Override
    public void travelToStageOrigin(DocumentId document, GridPoint origin) {
        travels.add(origin);
    }

    /** The stage origins the windows were asked to travel to, in order. */
    public List<GridPoint> travels() {
        return travels;
    }

    @Override
    public StepPacer stepPacer() {
        return pacer;
    }

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        EditorAccess access = () -> circuits.entrySet().stream()
                .map(entry -> new DocumentSummary(entry.getKey(), entry.getKey().value(), entry.getValue().version(),
                        entry.getValue().history().isModified()))
                .toList();
        return run(() -> work.apply(access));
    }

    @Override
    public <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        Circuit circuit = circuits.get(document);
        if (circuit == null) {
            return CompletableFuture.failedFuture(new AutomationException(AutomationError.DOCUMENT_CLOSED, "closed"));
        }
        return run(() -> work.apply(circuit));
    }

    private <T> CompletableFuture<T> run(Supplier<T> work) {
        try {
            return CompletableFuture.completedFuture(onEditor(work));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** One open circuit: its version grows with every change of its history, like {@code OpenDocument}. */
    public static final class Circuit implements EditableCircuit {

        private final CircuitDocument document;
        private final ComponentTypeRegistry registry;
        private final History history;
        private long changeCount;

        Circuit(CircuitDocument document) {
            this.document = document;
            this.registry = BASE_REGISTRY.copy();
            this.registry.addResolver(SubcircuitTypes.resolverFor(document));
            this.history = new History(document);
            history.addListener(changed -> changeCount++);
        }

        @Override
        public CircuitDocument document() {
            return document;
        }

        @Override
        public ComponentTypeRegistry registry() {
            return registry;
        }

        @Override
        public History history() {
            return history;
        }

        @Override
        public DocumentVersion version() {
            return new DocumentVersion(changeCount);
        }
    }

    /** A clock the test moves and a timer whose waits the test lets go. */
    public static final class FakePacer implements StepPacer {

        private Instant now = Instant.parse("2026-10-21T12:00:00Z");
        private final List<Wait> waits = new ArrayList<>();
        private final BlockingQueue<Duration> requested = new LinkedBlockingQueue<>();

        private record Wait(Instant due, CompletableFuture<Void> done) {
        }

        @Override
        public synchronized Instant now() {
            return now;
        }

        @Override
        public synchronized CompletableFuture<Void> after(Duration delay) {
            CompletableFuture<Void> done = new CompletableFuture<>();
            waits.add(new Wait(now.plus(delay), done));
            requested.add(delay);
            return done;
        }

        /** Moves the clock and lets go every wait that is due. */
        public void advance(Duration amount) {
            List<CompletableFuture<Void>> due = new ArrayList<>();
            synchronized (this) {
                now = now.plus(amount);
                waits.removeIf(wait -> {
                    if (!wait.due().isAfter(now)) {
                        due.add(wait.done());
                        return true;
                    }
                    return false;
                });
            }
            due.forEach(done -> done.complete(null));
        }

        /** The delay of the next wait asked for, waiting for a call on another thread to ask for it. */
        public Duration nextRequestedDelay() throws InterruptedException {
            Duration delay = requested.poll(10, TimeUnit.SECONDS);
            if (delay == null) {
                throw new AssertionError("no pause was asked for");
            }
            return delay;
        }
    }
}
