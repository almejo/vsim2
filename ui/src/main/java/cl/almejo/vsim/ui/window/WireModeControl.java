package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.toolbar.SplitButtonMode;
import cl.almejo.vsim.ui.tools.WireMode;

import javax.swing.Action;
import java.util.List;

/**
 * How the Wire tool draws, for one editor view: the Cable split button (its ▾ picks «Continuo» or «Por trozos») and
 * the check action behind Editar ▸ «Cable continuo». The mode is one for the whole app and lives in the preferences
 * ({@code splitButton.wire}): both parts follow a change made anywhere (another tab or window, the button's ▾, the
 * item), as the screenshot button does. {@link #dispose()} takes the listener back.
 */
final class WireModeControl {

    private final AppPreferences preferences;
    private final SplitButton button;
    private final Action continuousAction;
    private final Runnable preferencesListener = this::followThePreference;

    /** {@code activateTool} runs when the button or a mode in its ▾ is pressed (it chooses the Wire tool). */
    WireModeControl(AppPreferences preferences, Runnable activateTool) {
        this.preferences = preferences;
        List<SplitButtonMode> modes = List.of(
                new SplitButtonMode(WireMode.CONTINUOUS.id(), Icons.get(WireModeIcons.CONTINUOUS, Icons.TOOLBAR_SIZE),
                        Messages.text("wire.mode.continuous"), Messages.text("wire.mode.continuous.description")),
                new SplitButtonMode(WireMode.PIECES.id(), Icons.get(WireModeIcons.PIECES, Icons.TOOLBAR_SIZE),
                        Messages.text("wire.mode.pieces"), Messages.text("wire.mode.pieces.description")));
        button = SplitButton.forTool(WireMode.PREFERENCE_ID, Messages.text("wire.menuTitle"), modes, preferences,
                mode -> activateTool.run());
        continuousAction = Actions.of(Messages.text("action.wireContinuous"), null, () -> button.setMode(
                isContinuous() ? WireMode.PIECES.id() : WireMode.CONTINUOUS.id()));
        continuousAction.putValue(Action.SMALL_ICON, Icons.get(WireModeIcons.CONTINUOUS, Icons.MENU_SIZE));
        button.addModeListener(mode -> refreshItem());
        refreshItem();
        preferences.addListener(preferencesListener);
    }

    SplitButton button() {
        return button;
    }

    /** The check action of Editar ▸ «Cable continuo»; its {@code SELECTED_KEY} says whether the mode is continuous. */
    Action continuousAction() {
        return continuousAction;
    }

    private boolean isContinuous() {
        return WireMode.CONTINUOUS.id().equals(button.mode().id());
    }

    private void refreshItem() {
        continuousAction.putValue(Action.SELECTED_KEY, isContinuous());
    }

    /** A mode chosen elsewhere (another tab or window) shows here too; only when it differs, so nothing loops. */
    private void followThePreference() {
        preferences.splitButtonMode(WireMode.PREFERENCE_ID)
                .filter(chosen -> WireMode.CONTINUOUS.id().equals(chosen) || WireMode.PIECES.id().equals(chosen))
                .filter(chosen -> !chosen.equals(button.mode().id()))
                .ifPresent(button::setMode);
    }

    void dispose() {
        preferences.removeListener(preferencesListener);
    }
}
