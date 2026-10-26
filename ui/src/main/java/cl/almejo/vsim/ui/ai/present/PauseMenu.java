package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The «Pausa» submenu of Presentar: one radio per choice in {@link #CHOICES_MILLIS}, shared by the Ver ▸ «Ritmo de
 * Presentar» menu and the floating panel's menu. Picking a radio hands its milliseconds to the consumer; {@link
 * #select(int)} follows the preference without calling it. EDT only.
 */
final class PauseMenu extends JMenu {

    /** The pauses the menu offers, within {@link PresentationSettings#MINIMUM_PAUSE} … {@code MAXIMUM_PAUSE}. */
    static final List<Integer> CHOICES_MILLIS = List.of(300, 500, 1000, 2000, 3000, 5000);

    private final List<JMenuItem> items = new ArrayList<>();

    PauseMenu(Consumer<Integer> onPicked) {
        super(Messages.text("ai.present.pause"));
        ButtonGroup group = new ButtonGroup();
        for (int millis : CHOICES_MILLIS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    Messages.format("ai.present.pause.seconds", millis / 1000.0));
            item.addActionListener(event -> onPicked.accept(millis));
            group.add(item);
            items.add(item);
            add(item);
        }
    }

    /** Marks the radio of {@code millis}. */
    void select(int millis) {
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setSelected(CHOICES_MILLIS.get(index) == millis);
        }
    }

    List<JMenuItem> items() {
        return List.copyOf(items);
    }
}
