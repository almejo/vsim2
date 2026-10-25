package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionsSheetTest {

    @Test
    void theSheetHasABoldTitleAndTheTwoGroupsEachWithItsPins() {
        ConnectionsSheet sheet = new ConnectionsSheet(question(false));
        assertThat(labelNamed(sheet, "heading").getText()).isEqualTo(Messages.text("shape.connections.heading"));
        assertThat(labelNamed(sheet, "heading").getFont().isBold()).isTrue();
        assertThat(labelNamed(sheet, "disconnected").getText())
                .contains(Messages.text("shape.connections.disconnected"))
                .contains("<b>U5</b>").contains("<b>B</b>").contains("<b>U7</b>").contains("<b>Cout</b>");
        assertThat(labelNamed(sheet, "kept").getText()).contains(Messages.text("shape.connections.kept"))
                .contains("<b>U9</b>").contains("<b>Op</b>").contains("S2");
    }

    @Test
    void thePiecesThatChangeWithoutProblemsAreNamed() {
        ConnectionsSheet sheet = new ConnectionsSheet(question(false));
        assertThat(labelNamed(sheet, "others").getText())
                .contains(Messages.format("shape.connections.others.one", "U3"));
    }

    @Test
    void aGroupWithNothingInItDoesNotShow() {
        ConnectionsSheet onlyKept = new ConnectionsSheet(new ConnectionsQuestion(List.of(),
                List.of(new ConnectionsQuestion.Entry("U9", "Op", "S2")), List.of(), false));
        assertThat(find(onlyKept.content(), "disconnected")).isEmpty();
        assertThat(find(onlyKept.content(), "others")).isEmpty();
        assertThat(find(onlyKept.content(), "kept")).hasSize(1);
    }

    @Test
    void volverThenAplicarAndConnectByHandAreTheButtonsAndTheLastIsTheDefault() {
        ConnectionsSheet sheet = new ConnectionsSheet(question(false));
        assertThat(buttons(sheet)).extracting(JButton::getText).containsExactly(
                Messages.text("shape.connections.back"), Messages.text("shape.connections.apply"));
        assertThat(sheet.defaultButton().getText()).isEqualTo(Messages.text("shape.connections.apply"));
    }

    @Test
    void theButtonsSitAtTheRightEdgeOfTheSheet() {
        ConnectionsSheet sheet = new ConnectionsSheet(question(false));
        sheet.content().setSize(sheet.content().getPreferredSize());
        layout(sheet.content());
        JButton apply = sheet.defaultButton();
        Rectangle applyBounds = SwingUtilities.convertRectangle(apply.getParent(), apply.getBounds(), sheet.content());
        assertThat(applyBounds.x + applyBounds.width).isGreaterThanOrEqualTo(sheet.content().getWidth() - 24);
        assertThat(applyBounds.x).isGreaterThan(sheet.content().getWidth() / 2);
    }

    @Test
    void whenThePieceItselfIsBlockedOnlyVolverIsOffered() {
        ConnectionsSheet sheet = new ConnectionsSheet(question(true));
        assertThat(buttons(sheet)).extracting(JButton::getText).containsExactly(Messages.text("shape.connections.back"));
        assertThat(sheet.defaultButton().getText()).isEqualTo(Messages.text("shape.connections.back"));
        assertThat(labelNamed(sheet, "blocked").getText()).contains(Messages.text("shape.connections.blocked"));
    }

    @Test
    void theAnswerIsAplicarOnlyWhenThatButtonIsPressed() {
        ConnectionsSheet applied = new ConnectionsSheet(question(false));
        applied.defaultButton().doClick();
        assertThat(applied.confirmed()).isTrue();
        ConnectionsSheet backed = new ConnectionsSheet(question(false));
        buttons(backed).getFirst().doClick();
        assertThat(backed.confirmed()).isFalse();
    }

    @Test
    void entriesWithMarkupInTheirNamesAreShownAsText() {
        ConnectionsSheet sheet = new ConnectionsSheet(new ConnectionsQuestion(
                List.of(new ConnectionsQuestion.Entry("<i>U1</i>", "A&B", "")), List.of(), List.of(), false));
        assertThat(labelNamed(sheet, "disconnected").getText()).contains("&lt;i&gt;U1&lt;/i&gt;").contains("A&amp;B");
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) { layout(nested); }
        }
    }

    private static ConnectionsQuestion question(boolean blocked) {
        return new ConnectionsQuestion(
                List.of(new ConnectionsQuestion.Entry("U5", "B", ""), new ConnectionsQuestion.Entry("U7", "Cout", "")),
                List.of(new ConnectionsQuestion.Entry("U9", "Op", "S2")), List.of("U3"), blocked);
    }

    private static JLabel labelNamed(ConnectionsSheet sheet, String name) {
        return (JLabel) find(sheet.content(), name).getFirst();
    }

    private static List<JButton> buttons(ConnectionsSheet sheet) {
        return all(sheet.content()).stream().filter(JButton.class::isInstance).map(JButton.class::cast).toList();
    }

    private static List<Component> find(Container root, String name) {
        return all(root).stream().filter(component -> name.equals(component.getName())).toList();
    }

    private static List<Component> all(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container container) { found.addAll(all(container)); }
        }
        return found;
    }
}
