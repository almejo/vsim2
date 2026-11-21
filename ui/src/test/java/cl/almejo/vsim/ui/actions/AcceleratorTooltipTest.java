package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

/** The tooltip says the key the action has now and, while it is disabled, why (I1, I15). */
class AcceleratorTooltipTest {

    private static final KeyStroke DELETE = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);

    private static String description(Action action) {
        return (String) action.getValue(Action.SHORT_DESCRIPTION);
    }

    @Test
    void aShownCanvasKeyEndsTheTooltipAndFollowsAReassignment() {
        Action delete = Actions.of("Borrar", () -> { });
        AcceleratorTooltip.install(delete, () -> "Borrar la selección");

        delete.putValue(Actions.SHOWN_ACCELERATOR, DELETE);
        assertThat(description(delete)).isEqualTo("Borrar la selección (" + KeyNames.describe(DELETE) + ")");

        KeyStroke backspace = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);
        delete.putValue(Actions.SHOWN_ACCELERATOR, backspace);
        assertThat(description(delete)).isEqualTo("Borrar la selección (" + KeyNames.describe(backspace) + ")");
    }

    @Test
    void aDisabledActionSaysWhyAfterItsKey() {
        Action delete = Actions.of("Borrar", () -> { });
        delete.putValue(Actions.SHOWN_ACCELERATOR, DELETE);
        AcceleratorTooltip.install(delete, () -> "Borrar la selección");
        Actions.disabledBecause(delete, () -> "Selecciona algo primero");

        delete.setEnabled(false);
        assertThat(description(delete)).isEqualTo(Messages.format("tooltip.withReason",
                "Borrar la selección (" + KeyNames.describe(DELETE) + ")", "Selecciona algo primero"));

        delete.setEnabled(true);
        assertThat(description(delete)).isEqualTo("Borrar la selección (" + KeyNames.describe(DELETE) + ")");
    }

    @Test
    void anActionWithoutItsOwnTooltipShowsOnlyTheReasonWhileDisabled() {
        Action cut = Actions.of("Cortar", () -> { });
        Actions.disabledBecause(cut, () -> "Selecciona algo primero");

        cut.setEnabled(false);
        assertThat(description(cut)).isEqualTo("Selecciona algo primero");
        assertThat(cut.getValue(Actions.DISABLED_REASON)).isNotNull();

        cut.setEnabled(true);
        assertThat(description(cut)).isNull();
    }

    @Test
    void theReasonIsAskedEachTimeTheActionTurnsOff() {
        String[] reason = {"Selecciona algo primero"};
        Action configure = Actions.of("Configurar…", () -> { });
        Actions.disabledBecause(configure, () -> reason[0]);
        configure.setEnabled(false);

        reason[0] = "Selecciona un solo componente";
        configure.setEnabled(true);
        configure.setEnabled(false);

        assertThat(description(configure)).isEqualTo("Selecciona un solo componente");
        assertThat(Actions.disabledReason(configure)).contains("Selecciona un solo componente");
    }
}
