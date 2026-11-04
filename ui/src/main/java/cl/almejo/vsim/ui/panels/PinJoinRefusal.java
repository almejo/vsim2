package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.command.PinJoinCheck;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Map;
import java.util.Optional;

/**
 * Why a parameter change is refused because it would join a pin to the wrong wire ({@link PinJoinCheck}), as the
 * sentence the editor shows: Propiedades, «Configurar…», «Bits por pin» and the «Grupos» window all say it the same
 * way, and change nothing.
 */
public final class PinJoinRefusal {

    private PinJoinRefusal() {
    }

    /**
     * The sentence that refuses giving part {@code id} {@code rawValues}, or empty when the change joins nothing it
     * should not (or the part is gone). The values must be valid for the part.
     */
    public static Optional<String> of(OpenDocument openDocument, ElementId id, Map<String, Object> rawValues) {
        if (openDocument.document().component(id).isEmpty()) {
            return Optional.empty();
        }
        return PinJoinCheck.find(openDocument.document(), openDocument.registry(), id, rawValues)
                .map(PinJoinRefusal::sentence);
    }

    private static String sentence(PinJoinCheck.PinJoin join) {
        return switch (join.kind()) {
            case NEW_PIN_TOUCHES -> Messages.text("properties.newPinTouches");
            case JOINS_ANOTHER_NET -> Messages.format("groups.pinJoinsAnotherNet", join.pinName());
        };
    }
}
