package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineDraft;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One machine's test, shared by every view of the machine that tests it (fix round 2 of hito 4): the draft with its
 * steps, and whether it is over or closing. Each view has its own {@link MachineTestMode} (its bar, border, table,
 * chosen test and column) on this test; an edit in one view is the others' too, and leaving the test in one view
 * («Salir de la prueba», «Aplicar», «Dejar como estaba», a close that settles it) leaves it in all. A view that goes
 * while others test only leaves; the test ends, unwritten, with its last view. One test per machine
 * ({@link MachineTestsUnderWay}). EDT only.
 */
final class MachineTestUnderWay {

    private final OpenDocument circuit;
    private final MachineDraft draft;
    private final List<MachineTestMode> views = new ArrayList<>();
    private final Runnable forget;
    private boolean over;
    /** A close decided and written, waiting for «¿Guardar cambios?»: the guards ask nothing more meanwhile. */
    private boolean closing;

    private MachineTestUnderWay(OpenDocument circuit, String definitionId, MachineDraft draft) {
        this.circuit = Objects.requireNonNull(circuit, "circuit");
        this.draft = Objects.requireNonNull(draft, "draft");
        this.forget = MachineTestsUnderWay.add(circuit, definitionId, this);
        MachineTestsUnderWay.changed(circuit);
    }

    /** The test of {@code definitionId} in {@code circuit} begins on {@code draft}: it is the machine's test now. */
    static MachineTestUnderWay begin(OpenDocument circuit, String definitionId, MachineDraft draft) {
        return new MachineTestUnderWay(circuit, definitionId, draft);
    }

    /** The draft every view of the test shows and edits. */
    MachineDraft draft() {
        return draft;
    }

    /** The views testing now, in the order they joined. */
    List<MachineTestMode> views() {
        return List.copyOf(views);
    }

    /** {@code view} tests too from now on. */
    void joined(MachineTestMode view) {
        if (!over && !views.contains(view)) {
            views.add(view);
        }
    }

    /** {@code view} stopped testing: the last one to go ends the test, writing nothing. */
    void left(MachineTestMode view) {
        views.remove(view);
        if (views.isEmpty()) {
            end();
        }
    }

    /** The draft changed (in any view): every view follows it, and autosave copies it again. */
    void changed() {
        MachineTestsUnderWay.changed(circuit);
        views().forEach(MachineTestMode::draftChanged);
    }

    /** Ends the test in every view without writing anything; runs once. */
    void end() {
        if (over) {
            return;
        }
        over = true;
        forget.run();
        MachineTestsUnderWay.changed(circuit);
        List<MachineTestMode> leaving = views();
        views.clear();
        leaving.forEach(MachineTestMode::takeOut);
    }

    /** Whether the test ended. */
    boolean isOver() {
        return over;
    }

    /** Whether a close was decided and written, waiting for the save question. */
    boolean isClosing() {
        return closing;
    }

    void setClosing(boolean closing) {
        this.closing = closing;
    }
}
