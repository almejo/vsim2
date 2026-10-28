package cl.almejo.vsim.ui.memory;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Lets a text field take only the digits of the grid's current radix (shown upper case), at most the digits a word of
 * the memory's width needs: other keys are ignored as they are typed, not refused afterwards. The radix is read on
 * every edit, so switching Hex / Dec / Bin needs no new filter.
 */
final class MemoryDigitFilter extends DocumentFilter {

    private final MemoryGridModel model;

    MemoryDigitFilter(MemoryGridModel model) {
        this.model = model;
    }

    @Override
    public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
            throws BadLocationException {
        replace(bypass, offset, 0, text, attributes);
    }

    @Override
    public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
            throws BadLocationException {
        if (text == null || text.isEmpty()) {
            super.replace(bypass, offset, length, text, attributes);
            return;
        }
        int kept = bypass.getDocument().getLength() - length;
        String accepted = model.digitsOnly(text, kept);
        if (!accepted.isEmpty()) {
            super.replace(bypass, offset, length, accepted, attributes);
        }
    }
}
