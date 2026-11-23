package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import net.miginfocom.swing.MigLayout;

import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The bar over a machine's diagram in test mode (plan 2026-11-14, hito 4; mockup flow F): «Modo prueba · lo que pase
 * aquí no afecta al circuito» with «Salir de la prueba» while the diagram is as in the circuit, and «Modo prueba ·
 * cambiaste el diagrama: es un borrador…» with «Aplicar» and «Dejar como estaba» once it changed; «La máquina cambió
 * afuera» is added when the circuit's machine changed meanwhile. A real panel, so its buttons take the focus and a
 * screen reader reads it. Its colour is the scheme's, read each time it is painted.
 */
final class MachineTestBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Supplier<ColorScheme> scheme;
    private final JLabel message = new JLabel();
    private final JButton exitButton = new JButton(Messages.text("machine.test.exit"));
    private final JButton applyButton = new JButton(Messages.text("machine.test.apply"));
    private final JButton keepButton = new JButton(Messages.text("machine.test.keep"));

    /**
     * @param exit  «Salir de la prueba»
     * @param apply «Aplicar»
     * @param keep  «Dejar como estaba»
     */
    MachineTestBar(Supplier<ColorScheme> scheme, Runnable exit, Runnable apply, Runnable keep) {
        super(new MigLayout("insets 4lp 8lp 4lp 8lp, gap 8lp", "[grow, fill]push[][][]", "[center]"));
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        setOpaque(true);
        exitButton.setToolTipText(Messages.text("machine.test.exit.tooltip"));
        applyButton.setToolTipText(Messages.text("machine.test.apply.tooltip"));
        keepButton.setToolTipText(Messages.text("machine.test.keep.tooltip"));
        exitButton.addActionListener(event -> exit.run());
        applyButton.addActionListener(event -> apply.run());
        keepButton.addActionListener(event -> keep.run());
        add(message, "wmin 0");
        add(exitButton);
        add(applyButton);
        add(keepButton);
        getAccessibleContext().setAccessibleName(Messages.text("machine.test.bar.name"));
        show(false, false);
    }

    /** Shows the bar for a draft whose diagram {@code diagramChanged}, and whether the circuit's machine changed. */
    void show(boolean diagramChanged, boolean changedOutside) {
        String text = Messages.text(diagramChanged ? "machine.test.bar.changed" : "machine.test.bar.clean");
        if (changedOutside) {
            text = Messages.format("machine.test.bar.withOutside", text, Messages.text("machine.test.bar.outside"));
        }
        message.setText(text);
        message.setToolTipText(text);
        getAccessibleContext().setAccessibleDescription(text);
        exitButton.setVisible(!diagramChanged);
        applyButton.setVisible(diagramChanged);
        keepButton.setVisible(diagramChanged);
        revalidate();
        repaint();
    }

    /** What the bar says now. */
    String message() {
        return message.getText();
    }

    JButton exitButton() {
        return exitButton;
    }

    JButton applyButton() {
        return applyButton;
    }

    JButton keepButton() {
        return keepButton;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        ColorScheme colours = scheme.get();
        graphics.setColor(MachineColors.testModeBar(colours));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        message.setForeground(colours.text());
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel() {
                private static final long serialVersionUID = 1L;

                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.TOOL_BAR;
                }
            };
        }
        return accessibleContext;
    }
}
