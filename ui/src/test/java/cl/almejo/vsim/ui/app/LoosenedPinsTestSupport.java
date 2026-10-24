package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.LoosensPins;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;
import java.util.Set;

/** Lets tests outside this package mark pins as left loose, through a command that changes nothing else. */
public final class LoosenedPinsTestSupport {

    private LoosenedPinsTestSupport() {
    }

    /** Executes one undo step that marks {@code pins} as loosened, as an accepted shape change would. */
    public static void set(OpenDocument openDocument, Set<PinRef> pins) {
        openDocument.execute(new MarkPins(List.copyOf(pins)));
    }

    private record MarkPins(List<PinRef> loosenedPins) implements Command, LoosensPins {

        @Override
        public void apply(CircuitDocument document) {
        }

        @Override
        public void revert(CircuitDocument document) {
        }

        @Override
        public String description() {
            return "mark loosened pins";
        }
    }
}
