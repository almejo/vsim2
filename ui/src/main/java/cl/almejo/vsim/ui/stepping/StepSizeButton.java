package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.waveform.TimeAxis;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * The small arrow right after «Avanzar 10 t» in the toolbar (M6.1-B, spec §4.4), which makes it a split button: a
 * menu with 1, 10, 100, 1 000 and 10 000 time units and «Otro…». It has no action, so the toolbar's icon and
 * shortcut-tooltip handling leave it alone.
 */
final class StepSizeButton extends JButton {

    static final List<Long> SIZES = List.of(1L, 10L, 100L, 1_000L, 10_000L);

    private final JPopupMenu menu = new JPopupMenu();

    /** {@code choice} gets a listed size; {@code other} runs for «Otro…». */
    StepSizeButton(LongConsumer choice, Runnable other) {
        super(Icons.get(StepIcons.SIZE_MENU, Icons.MENU_SIZE));
        setToolTipText(Messages.text("stepSize.tip"));
        setFocusable(false);
        for (long size : SIZES) {
            JMenuItem item = new JMenuItem(TimeAxis.format(size));
            item.addActionListener(event -> choice.accept(size));
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem otherItem = new JMenuItem(Messages.text("stepSize.other"));
        otherItem.addActionListener(event -> other.run());
        menu.add(otherItem);
        addActionListener(event -> menu.show(this, 0, getHeight()));
    }

    /** The menu the arrow opens (tests click its items without showing it). */
    JPopupMenu menu() {
        return menu;
    }
}
