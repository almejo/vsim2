package cl.almejo.vsim.ui.theme;

import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;

/**
 * A read-only error text that looks like a label but wraps by words at the width it is given (a narrow sidebar), in
 * {@link ReadableColors#errorText()} read at paint time. Its minimum and preferred widths are nil, so a layout may
 * shrink it and a long sentence never widens what holds it (a sidebar that would eat the canvas, a dialog already
 * shown): give it {@code growx} and it takes the width there is, its height following the lines it wraps into.
 */
public final class WrappedErrorText extends JTextArea {

    public WrappedErrorText(String text) {
        super(text);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setBorder(null);
        setFont(UIManager.getFont("Label.font"));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(UIManager.getFont("Label.font"));
        setBorder(null);
    }

    @Override
    public Color getForeground() {
        return ReadableColors.errorText();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, super.getMinimumSize().height);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, super.getPreferredSize().height);
    }
}
