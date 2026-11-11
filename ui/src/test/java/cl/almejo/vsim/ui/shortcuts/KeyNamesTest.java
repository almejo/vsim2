package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** Keys named in the user's language, the same in the sheet, the palette, the tooltips and the chips. */
class KeyNamesTest {

    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    @AfterEach
    void backToSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static KeyStroke stroke(int keyCode, int modifiers) {
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    @Test
    void controlShiftSIsCtrlMayusSInSpanishAndCtrlShiftSInEnglish() {
        Messages.initialize(Messages.SPANISH);
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_S, CONTROL | SHIFT), false)).isEqualTo("Ctrl+Mayús+S");

        Messages.initialize(Locale.ENGLISH);
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_S, CONTROL | SHIFT), false)).isEqualTo("Ctrl+Shift+S");
    }

    @Test
    void namedKeysUseTheWordsOfTheSpanishKeyboard() {
        Messages.initialize(Messages.SPANISH);

        assertThat(KeyNames.describe(stroke(KeyEvent.VK_DELETE, 0), false)).isEqualTo("Supr");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_BACK_SPACE, 0), false)).isEqualTo("Retroceso");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_SPACE, 0), false)).isEqualTo("Espacio");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_INSERT, 0), false)).isEqualTo("Insert");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_ESCAPE, 0), false)).isEqualTo("Esc");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_ENTER, 0), false)).isEqualTo("Enter");
    }

    @Test
    void englishNamesTheSameKeysInEnglish() {
        Messages.initialize(Locale.ENGLISH);

        assertThat(KeyNames.describe(stroke(KeyEvent.VK_DELETE, 0), false)).isEqualTo("Delete");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_BACK_SPACE, 0), false)).isEqualTo("Backspace");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_SPACE, 0), false)).isEqualTo("Space");
    }

    @Test
    void lettersDigitsFunctionKeysAndSymbolsAreTheirOwnName() {
        Messages.initialize(Messages.SPANISH);

        assertThat(KeyNames.describe(stroke(KeyEvent.VK_1, SHIFT), false)).isEqualTo("Mayús+1");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_F11, 0), false)).isEqualTo("F11");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_EQUALS, CONTROL), false)).isEqualTo("Ctrl+=");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_MINUS, CONTROL), false)).isEqualTo("Ctrl+-");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_L, CONTROL | InputEvent.ALT_DOWN_MASK | SHIFT), false))
                .isEqualTo("Ctrl+Alt+Mayús+L");
    }

    @Test
    void aTypedCharacterIsThatCharacter() {
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke('?'), false)).isEqualTo("?");
    }

    @Test
    void theMacNotationIsSymbolsWithoutPlus() {
        Messages.initialize(Messages.SPANISH);

        assertThat(KeyNames.describe(stroke(KeyEvent.VK_Z, InputEvent.META_DOWN_MASK | SHIFT), true))
                .isEqualTo("⇧⌘Z");
        assertThat(KeyNames.describe(stroke(KeyEvent.VK_DELETE, 0), true)).isEqualTo("Supr");
    }

    @Test
    void theKeysOfAStrokeAreItsModifiersThenItsKey() {
        Messages.initialize(Messages.SPANISH);

        assertThat(KeyNames.keys(stroke(KeyEvent.VK_S, CONTROL | SHIFT), false))
                .containsExactly("Ctrl", "Mayús", "S");
        assertThat(KeyNames.keys(stroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK | SHIFT), true))
                .containsExactly("⇧", "⌘", "S");
    }

    @Test
    void aModifierKeyPressedAloneIsNamedLikeTheModifier() {
        Messages.initialize(Messages.SPANISH);

        assertThat(KeyNames.keys(stroke(KeyEvent.VK_ALT, 0), false)).containsExactly("Alt");
        assertThat(KeyNames.keys(stroke(KeyEvent.VK_SHIFT, 0), false)).containsExactly("Mayús");
        assertThat(KeyNames.keys(stroke(KeyEvent.VK_CONTROL, 0), false)).containsExactly("Ctrl");
        assertThat(KeyNames.keys(stroke(KeyEvent.VK_ALT, 0), true)).containsExactly("⌥");
        assertThat(KeyNames.keys(stroke(KeyEvent.VK_SHIFT, 0), true)).containsExactly("⇧");
    }
}
