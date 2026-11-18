package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * The left palette of a machine's tab (spec 2026-11-14 §1, the mockup's «Paleta»): the drawing tools, «Estado»,
 * «Transición» and «Nota» (a sticky note, hito 3), and under «Y después» the next steps «Convertir a circuito…»,
 * shown disabled with «Llega en un próximo hito» until it arrives, and «Probar…» (the test mode, hito 4; disabled
 * with «Ya estás probando» while it is on). A tool is chosen here (one at a time, or
 * none) and used once on the diagram ({@link MachineTool}): a click places a state or a note, a drag joins two
 * states.
 */
public final class MachinePalette extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int GAP = 4;
    private static final int PADDING = 8;

    private final JToggleButton stateButton = toolButton("machine.palette.state", "machine.palette.stateHint");
    private final JToggleButton transitionButton = toolButton("machine.palette.transition",
            "machine.palette.transitionHint");
    private final JToggleButton noteButton = toolButton("machine.palette.note", "machine.palette.noteHint");
    private final JButton convertButton = laterButton("machine.palette.convert");
    private final JButton testButton = fullWidth(hinted(new JButton(Messages.text("machine.palette.test")),
            "machine.palette.testHint"));
    private final JLabel nextHeader = new JLabel(Messages.text("machine.palette.next"));
    private final ButtonGroup tools = new ButtonGroup();

    public MachinePalette() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        tools.add(stateButton);
        tools.add(transitionButton);
        tools.add(noteButton);
        add(stateButton);
        add(Box.createVerticalStrut(GAP));
        add(transitionButton);
        add(Box.createVerticalStrut(GAP));
        add(noteButton);
        add(Box.createVerticalStrut(2 * PADDING));
        nextHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(nextHeader);
        add(Box.createVerticalStrut(GAP));
        add(convertButton);
        add(Box.createVerticalStrut(GAP));
        add(testButton);
        add(Box.createVerticalGlue());
        applyTheme();
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

    /** Chooses no tool: a placed state or a drawn arrow, or Esc, puts the diagram back to selecting. */
    public void clearTool() {
        tools.clearSelection();
    }

    /** «Convertir a circuito…» (hito 5). */
    public JButton convertButton() {
        return convertButton;
    }

    /** «Probar…»: starts the test mode (hito 4; {@link MachineMode#startTest()}). */
    public JButton testButton() {
        return testButton;
    }

    /** The header's font follows the theme: set again on every theme change. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (nextHeader != null) {
            applyTheme();
        }
    }

    private void applyTheme() {
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            nextHeader.setFont(font.deriveFont(Font.BOLD));
        }
    }

    private static JToggleButton toolButton(String nameKey, String hintKey) {
        JToggleButton button = new JToggleButton(Messages.text(nameKey));
        button.setToolTipText(Messages.text(hintKey));
        return fullWidth(button);
    }

    private static <T extends AbstractButton> T hinted(T button, String hintKey) {
        button.setToolTipText(Messages.text(hintKey));
        return button;
    }

    private static JButton laterButton(String nameKey) {
        JButton button = new JButton(Messages.text(nameKey));
        button.setEnabled(false);
        button.setToolTipText(Messages.text("machine.comesLater"));
        return fullWidth(button);
    }

    private static <T extends AbstractButton> T fullWidth(T button) {
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        return button;
    }
}
