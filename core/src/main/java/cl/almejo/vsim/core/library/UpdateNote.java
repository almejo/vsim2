package cl.almejo.vsim.core.library;

import java.util.List;
import java.util.Objects;

/**
 * One line of what a definition update did to the placed pieces (spec §6.3 step 4): a message key of the UI bundle
 * and its arguments, such as {@code appearance.update.newPin.right} with «D» («El pin D se ubicó en el borde derecho»).
 */
public record UpdateNote(String messageKey, List<String> arguments) {

    public UpdateNote {
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }
}
