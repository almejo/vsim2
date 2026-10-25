package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The line under the canvas is one line: no hint of the editor is longer than the space it has. */
class ShapeEditorHintsTest {
    /** About what the line holds in the dialog's default width with the label font. */
    private static final int LONGEST_HINT = 125;

    @AfterEach
    void restoreTheLanguage() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void everyHintFitsTheLineInBothLanguages() {
        for (Locale locale : new Locale[]{Messages.SPANISH, Locale.ENGLISH}) {
            Messages.initialize(locale);
            for (ShapeTool tool : ShapeTool.values()) {
                for (boolean pinSelected : new boolean[]{false, true}) {
                    for (boolean nodeSelected : new boolean[]{false, true}) {
                        for (int figures : new int[]{0, 1, 12}) {
                            String hint = ShapeEditorTexts.hint(new ShapeEditorTexts.HintState(tool, pinSelected,
                                    false, figures, nodeSelected));
                            assertThat(hint.length()).as("%s %s tool=%s pin=%s node=%s figures=%d", locale, hint, tool,
                                    pinSelected, nodeSelected, figures).isLessThanOrEqualTo(LONGEST_HINT);
                        }
                    }
                }
            }
        }
    }
}
