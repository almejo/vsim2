package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.sound.TerminalSounds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The sounds of a document's Terminals (spec §8.6), read from the counters of each published screen
 * ({@link TerminalFrame#bellCount()}, {@link TerminalFrame#printedCount()}): new BEL bytes ring the bell once per
 * frame (several in one frame merge, as a 120 ms beep cannot tell them apart) or ask for a «Destello» through
 * {@code flashes}, as «Campana (BEL)» says; new characters click with «Golpeteo de teletipo» ({@link TerminalSounds}
 * limits the clicks). The first screen seen and a «Reiniciar» (the counters go down) are silent. Only Terminals (and
 * UART 16550s with their integrated screen) on the circuit itself, like the window. EDT only.
 */
public final class TerminalSoundController {

    private static final String BELL_SOUND = "sound";
    private static final String BELL_FLASH = "flash";
    private static final String BELL_BOTH = "both";

    /** The counters of one Terminal's last screen. */
    private record Heard(long bells, long printed) {
    }

    private final OpenDocument document;
    private final TerminalSounds sounds;
    private final Consumer<String> flashes;
    private final Map<String, Heard> heard = new HashMap<>();
    private final DocumentListener documentListener = this::documentChanged;
    private List<String> terminalIds = List.of();

    public TerminalSoundController(OpenDocument document, TerminalSounds sounds, Consumer<String> flashes) {
        this.document = Objects.requireNonNull(document, "document");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.flashes = Objects.requireNonNull(flashes, "flashes");
        document.document().addListener(documentListener);
        refreshTerminals();
    }

    /** Whether the circuit has a Terminal or a UART with its screen (nothing to listen to otherwise). */
    public boolean hasTerminals() {
        return !terminalIds.isEmpty();
    }

    /** Listens to {@code snapshot}'s screens. */
    public void poll(SimulationSnapshot snapshot) {
        for (String componentId : terminalIds) {
            VisibleState.terminalScreen(snapshot.componentState(componentId))
                    .ifPresent(state -> hear(componentId, state.frame()));
        }
    }

    /** Stops watching the document. */
    public void dispose() {
        document.document().removeListener(documentListener);
    }

    private void hear(String componentId, TerminalFrame frame) {
        Heard now = new Heard(frame.bellCount(), frame.printedCount());
        Heard before = heard.put(componentId, now);
        if (before == null || now.bells() < before.bells() || now.printed() < before.printed()) {
            return;
        }
        Optional<Component> component = document.document().component(new ElementId(componentId));
        if (component.isEmpty()) {
            return;
        }
        Map<String, Object> resolved = document.registry().typeOf(component.get())
                .resolveParameters(component.get().parameters());
        if (now.bells() > before.bells()) {
            ring(componentId, String.valueOf(resolved.get(TerminalTypes.BELL)));
        }
        if (now.printed() > before.printed() && Boolean.TRUE.equals(resolved.get(TerminalTypes.TELETYPE_SOUND))) {
            sounds.typed((int) Math.min(Integer.MAX_VALUE, now.printed() - before.printed()));
        }
    }

    private void ring(String componentId, String bell) {
        if (bell.equals(BELL_SOUND) || bell.equals(BELL_BOTH)) {
            sounds.bell();
        }
        if (bell.equals(BELL_FLASH) || bell.equals(BELL_BOTH)) {
            flashes.accept(componentId);
        }
    }

    private void documentChanged(CircuitDocument changed, DocumentChange change) {
        refreshTerminals();
    }

    private void refreshTerminals() {
        terminalIds = document.document().components().stream()
                .filter(component -> TerminalParts.showsScreen(document, component))
                .map(component -> component.id().value()).toList();
        heard.keySet().retainAll(terminalIds);
    }
}
