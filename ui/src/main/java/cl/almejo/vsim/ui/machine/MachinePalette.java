package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachinePaletteIcons.Kind;
import cl.almejo.vsim.ui.machine.MachinePaletteRows.ToolRow;
import cl.almejo.vsim.ui.paint.ColorScheme;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Dimension;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The left palette of a machine's tab (spec 2026-11-14 §1, the mockup's «Paleta»), its rows drawn as the circuit
 * palette's are ({@link MachinePaletteRows}): the drawing tools only, «Estado», «Transición», «Nota» (a sticky
 * note) and «Texto» (a free text). The machine-wide actions («Probar…», «Agregar a Mis
 * componentes») are in the top bar of the tab ({@code PlaceBar}). A tool is chosen here (one at a time, or none) and
 * used once on the diagram ({@link MachineTool}): a click places a state, a note or a text, a drag joins two
 * states.
 */
public final class MachinePalette extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int GAP = 4;
    private static final int PADDING = 8;

    private final JToggleButton stateButton;
    private final JToggleButton transitionButton;
    private final JToggleButton noteButton;
    private final JToggleButton textButton;
    private final ButtonGroup tools = new ButtonGroup();

    /** A palette whose icons are drawn in the colours {@code scheme} gives at each paint (the diagram's). */
    public MachinePalette(Supplier<ColorScheme> scheme) {
        Objects.requireNonNull(scheme, "scheme");
        stateButton = toolRow("machine.palette.state", "machine.palette.stateHint", Kind.STATE, scheme);
        transitionButton = toolRow("machine.palette.transition", "machine.palette.transitionHint", Kind.TRANSITION,
                scheme);
        noteButton = toolRow("machine.palette.note", "machine.palette.noteHint", Kind.NOTE, scheme);
        textButton = toolRow("machine.palette.text", "machine.palette.textHint", Kind.TEXT, scheme);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(PADDING), UIScale.scale(PADDING),
                UIScale.scale(PADDING), UIScale.scale(PADDING)));
        tools.add(stateButton);
        tools.add(transitionButton);
        tools.add(noteButton);
        tools.add(textButton);
        add(stateButton);
        add(Box.createVerticalStrut(UIScale.scale(GAP)));
        add(transitionButton);
        add(Box.createVerticalStrut(UIScale.scale(GAP)));
        add(noteButton);
        add(Box.createVerticalStrut(UIScale.scale(GAP)));
        add(textButton);
        add(Box.createVerticalGlue());
    }

    /** «Estado»: puts states on the diagram. */
    public JToggleButton stateButton() {
        return stateButton;
    }

    /** «Transición»: joins two states with an arrow. */
    public JToggleButton transitionButton() {
        return transitionButton;
    }

    /** «Nota»: sticks a note on the diagram with a click (the toolbar's «Nota adhesiva» chooses it too). */
    public JToggleButton noteButton() {
        return noteButton;
    }

    /** «Texto»: writes a free text on the diagram with a click (the toolbar's «Texto» chooses it too). */
    public JToggleButton textButton() {
        return textButton;
    }

    /** Chooses no tool: a placed state or a drawn arrow, or Esc, puts the diagram back to selecting. */
    public void clearTool() {
        tools.clearSelection();
    }

    private static ToolRow toolRow(String nameKey, String hintKey, Kind kind, Supplier<ColorScheme> scheme) {
        ToolRow row = new ToolRow(Messages.text(nameKey), MachinePaletteIcons.of(kind, scheme));
        row.setToolTipText(Messages.text(hintKey));
        return fullWidth(row);
    }

    private static <T extends AbstractButton> T fullWidth(T button) {
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        return button;
    }
}
