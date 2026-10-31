package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;

/**
 * Which parts of a circuit have a Terminal screen (its window, «Limpiar pantalla») and which take typing like a
 * Teclado: a Terminal and a Teclado, and a UART 16550 with «Pantalla: Integrada», which is both.
 */
final class TerminalParts {

    private TerminalParts() {
    }

    /** A Terminal, or a UART 16550 with its integrated screen. */
    static boolean showsScreen(OpenDocument document, Component component) {
        return component.typeId().equals(StandardComponentTypes.TERMINAL) || isIntegratedUart(document, component);
    }

    /** A UART 16550 with its integrated screen and keyboard. */
    static boolean isIntegratedUart(OpenDocument document, Component component) {
        return component.typeId().equals(StandardComponentTypes.UART_16550) && UartTypes.isIntegrated(
                document.registry().typeOf(component).resolveParameters(component.parameters()));
    }
}
