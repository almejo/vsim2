package cl.almejo.vsim.ui.about;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Where «Copiar información» puts the text. Injectable so tests never touch the system clipboard. */
@FunctionalInterface
public interface ClipboardTarget {

    void copy(String text);

    static ClipboardTarget system() {
        return text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
