package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The left column (spec §5.3): the circuit tree's slot over the palette, an accordion whose «Circuito» and «Paleta»
 * headers fold their panel (Alt+1, Alt+2), with the divider between them remembered.
 */
class LeftColumnTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new cl.almejo.vsim.core.library.ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.setSize(900, 600);
        layOut(view);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    /** Whether {@code component} and every container up to the view are visible, and it has room. */
    private boolean shown(Component component) {
        for (Component current = component; current != view; current = current.getParent()) {
            if (current == null || !current.isVisible()) {
                return false;
            }
        }
        return component.getHeight() > 0;
    }

    @Test
    void theComponentGoesAboveThePalette() {
        JLabel tree = new JLabel("árbol");

        view.addAbovePalette("Circuito", tree);
        layOut(view);

        assertThat(shown(tree)).isTrue();
        assertThat(shown(view.palette())).isTrue();
        assertThat(SwingUtilities.convertPoint(tree, 0, 0, view).y)
                .isLessThan(SwingUtilities.convertPoint(view.palette(), 0, 0, view).y);
        assertThat(view.leftColumn().title(LeftColumn.Section.CIRCUIT)).contains("Circuito");
        assertThat(view.leftColumn().title(LeftColumn.Section.PALETTE)).contains(Messages.text("leftColumn.palette"));
    }

    @Test
    void accordionFoldsOnePanelAndAltOneTogglesIt() {
        JLabel tree = new JLabel("árbol");
        view.addAbovePalette("Circuito", tree);
        layOut(view);
        int paletteHeight = view.palette().getHeight();
        Action circuit = view.leftColumn().toggleAction(LeftColumn.Section.CIRCUIT);
        Action palette = view.leftColumn().toggleAction(LeftColumn.Section.PALETTE);
        List<String> changes = new ArrayList<>();
        view.leftColumn().addFoldListener(() -> changes.add(view.leftColumn().folded().toString()));

        assertThat(circuit.getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK));
        assertThat(palette.getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK));
        assertThat(circuit.getValue(Action.SELECTED_KEY)).isEqualTo(true);

        perform(circuit);
        layOut(view);

        assertThat(view.leftColumn().isFolded(LeftColumn.Section.CIRCUIT)).isTrue();
        assertThat(circuit.getValue(Action.SELECTED_KEY)).isEqualTo(false);
        assertThat(shown(tree)).isFalse();
        assertThat(view.palette().getHeight()).as("the palette takes the space").isGreaterThan(paletteHeight);
        assertThat(changes).containsExactly("[CIRCUIT]");

        perform(circuit);
        perform(palette);
        layOut(view);

        assertThat(shown(tree)).isTrue();
        assertThat(shown(view.palette())).isFalse();
        assertThat(view.leftColumn().folded()).containsExactly(LeftColumn.Section.PALETTE);
    }

    @Test
    void theViewMenuHasBothPanelsWhichFoldOnlyWithAPanelAboveThePalette() {
        assertThat(texts(view.menu(MenuId.VIEW))).startsWith("Árbol del circuito", "Paleta", "---");
        Action circuit = view.leftColumn().toggleAction(LeftColumn.Section.CIRCUIT);
        Action palette = view.leftColumn().toggleAction(LeftColumn.Section.PALETTE);
        assertThat(circuit.isEnabled()).isFalse();
        assertThat(palette.isEnabled()).as("nothing would take the palette's space").isFalse();

        Runnable removal = view.addAbovePalette("Circuito", new JLabel("árbol"));
        assertThat(circuit.isEnabled()).isTrue();
        assertThat(palette.isEnabled()).isTrue();
        removal.run();
        assertThat(circuit.isEnabled()).isFalse();
        assertThat(palette.isEnabled()).isFalse();
    }

    @Test
    void theRemovalBringsBackThePaletteAlone() {
        JLabel tree = new JLabel("árbol");
        Runnable removal = view.addAbovePalette("Circuito", tree);

        removal.run();
        layOut(view);

        assertThat(SwingUtilities.isDescendingFrom(tree, view)).isFalse();
        assertThat(shown(view.palette())).isTrue();
        assertThat(view.leftColumn().title(LeftColumn.Section.CIRCUIT)).isEmpty();
        removal.run();
    }

    @Test
    void theDividerIsRememberedInThePreferences() {
        JLabel tree = new JLabel("árbol");
        view.addAbovePalette("Circuito", tree);
        layOut(view);
        JSplitPane split = (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, tree);

        split.setDividerLocation(123);

        assertThat(preferences.circuitTreeSplit()).hasValue(123);
    }

    @Test
    void aRememberedDividerIsUsedWhenTheComponentIsAdded() {
        preferences.setCircuitTreeSplit(150);
        JLabel tree = new JLabel("árbol");

        view.addAbovePalette("Circuito", tree);
        layOut(view);

        JSplitPane split = (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, tree);
        assertThat(split.getDividerLocation()).isEqualTo(150);
    }

    private JSplitPane splitOf(Component tree) {
        return (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, tree);
    }

    @Test
    void theDividerKeepsItsGripAndDragsWithTheMouse() {
        JLabel tree = new JLabel("árbol");
        view.addAbovePalette("Circuito", tree);
        layOut(view);
        JSplitPane split = splitOf(tree);
        javax.swing.plaf.basic.BasicSplitPaneDivider divider =
                ((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider();
        assertThat(divider.getParent()).as("the divider is still a child of the split").isSameAs(split);
        int before = split.getDividerLocation();

        divider.dispatchEvent(new java.awt.event.MouseEvent(divider, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0,
                2, 2, 1, false));
        divider.dispatchEvent(new java.awt.event.MouseEvent(divider, java.awt.event.MouseEvent.MOUSE_DRAGGED, 0,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 2, 42, 0, false));
        divider.dispatchEvent(new java.awt.event.MouseEvent(divider, java.awt.event.MouseEvent.MOUSE_RELEASED, 0, 0,
                2, 42, 1, false));

        assertThat(split.getDividerLocation()).isGreaterThan(before);
        assertThat(preferences.circuitTreeSplit()).hasValue(split.getDividerLocation());
    }

    @Test
    void foldingAndUnfoldingThePaletteBringsBothPanelsBack() {
        JLabel tree = new JLabel("árbol");
        view.addAbovePalette("Circuito", tree);
        layOut(view);
        int treeHeight = tree.getHeight();
        Action palette = view.leftColumn().toggleAction(LeftColumn.Section.PALETTE);

        perform(palette);
        layOut(view);
        assertThat(shown(tree)).isTrue();
        assertThat(shown(view.palette())).isFalse();
        perform(palette);
        layOut(view);

        assertThat(shown(tree)).isTrue();
        assertThat(shown(view.palette())).isTrue();
        assertThat(tree.getHeight()).as("the circuit keeps its previous height").isEqualTo(treeHeight);
        assertThat(splitOf(tree).getDividerLocation()).isGreaterThan(0);
    }

    @Test
    void foldingAndUnfoldingTheCircuitOrBothBringsBothPanelsBack() {
        JLabel tree = new JLabel("árbol");
        view.addAbovePalette("Circuito", tree);
        layOut(view);
        int treeHeight = tree.getHeight();
        Action circuit = view.leftColumn().toggleAction(LeftColumn.Section.CIRCUIT);
        Action palette = view.leftColumn().toggleAction(LeftColumn.Section.PALETTE);

        perform(circuit);
        perform(circuit);
        layOut(view);
        assertThat(shown(tree)).isTrue();
        assertThat(shown(view.palette())).isTrue();
        assertThat(tree.getHeight()).isEqualTo(treeHeight);

        perform(circuit);
        perform(palette);
        layOut(view);
        assertThat(shown(tree)).isFalse();
        assertThat(shown(view.palette())).isFalse();
        perform(palette);
        perform(circuit);
        layOut(view);

        assertThat(shown(tree)).isTrue();
        assertThat(shown(view.palette())).isTrue();
        assertThat(tree.getHeight()).isEqualTo(treeHeight);
    }
}
