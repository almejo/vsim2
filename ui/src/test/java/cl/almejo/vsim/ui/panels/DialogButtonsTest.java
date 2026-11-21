package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared dialog pieces: button order, default button, Escape, and never larger than the screen. */
class DialogButtonsTest {

    @Test
    void theActionButtonComesFirstAndCancelLast() {
        JButton action = new JButton("Crear");
        JButton cancel = new JButton("Cancelar");

        JPanel row = DialogButtons.row(action, cancel);

        assertThat(row.getComponents()).containsExactly(action, cancel);
        assertThat(((FlowLayout) row.getLayout()).getAlignment()).isEqualTo(FlowLayout.RIGHT);
    }

    @Test
    void installSetsTheDefaultButtonAndEscapePressesCancel() {
        JRootPane root = new JRootPane();
        JButton action = new JButton("Crear");
        JButton cancel = new JButton("Cancelar");
        AtomicInteger cancelled = new AtomicInteger();
        cancel.addActionListener(event -> cancelled.incrementAndGet());

        DialogButtons.install(root, action, cancel);

        assertThat(root.getDefaultButton()).isSameAs(action);
        var escape = root.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertThat(escape).isNotNull();
        escape.actionPerformed(new ActionEvent(root, 0, "escape"));
        assertThat(cancelled).hasValue(1);
    }

    @Test
    void escapeWorksFromAnyFocusedComponentOfTheWindow() {
        JRootPane root = new JRootPane();
        DialogButtons.install(root, new JButton(), new JButton());

        assertThat(root.getConditionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)))
                .isEqualTo(JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    @Test
    void aWiderDialogThanA1366ScreenIsShrunkToTheUsableArea() {
        Rectangle usable = new Rectangle(0, 28, 1366, 740); // a top bar of 28 px on a 1366 × 768 screen

        assertThat(DialogButtons.fitted(new Dimension(1872, 700), usable)).isEqualTo(new Dimension(1366, 700));
        assertThat(DialogButtons.fitted(new Dimension(656, 811), usable)).isEqualTo(new Dimension(656, 740));
    }

    @Test
    void aDialogThatFitsIsLeftAlone() {
        Rectangle usable = new Rectangle(0, 0, 1366, 768);

        assertThat(DialogButtons.fitted(new Dimension(500, 300), usable)).isEqualTo(new Dimension(500, 300));
    }

    @Test
    void theUsableAreaDiscountsTheScreenInsets() {
        Rectangle usable = DialogButtons.usable(new Rectangle(0, 0, 1366, 768), new Insets(28, 0, 40, 0));

        assertThat(usable).isEqualTo(new Rectangle(0, 28, 1366, 700));
    }

    @Test
    void theContentScrollsWhenTheWindowIsCut() {
        JPanel content = new JPanel();
        content.setPreferredSize(new Dimension(300, 200));

        var scroll = DialogButtons.scrolling(content);

        assertThat(scroll.getViewport().getView()).isSameAs(content);
        assertThat(scroll.getPreferredSize().width).isGreaterThanOrEqualTo(300);
        assertThat(scroll.getBorder()).isNull();
    }

    @Test
    void aWindowThatStickOutOfTheUsableAreaIsMovedInside() {
        Rectangle usable = new Rectangle(0, 28, 1366, 740);

        assertThat(DialogButtons.placed(new Rectangle(-300, -50, 1366, 740), usable))
                .isEqualTo(new Rectangle(0, 28, 1366, 740));
        assertThat(DialogButtons.placed(new Rectangle(1000, 600, 656, 400), usable))
                .isEqualTo(new Rectangle(710, 368, 656, 400));
        assertThat(DialogButtons.placed(new Rectangle(100, 100, 500, 300), usable))
                .isEqualTo(new Rectangle(100, 100, 500, 300));
    }
}
