package cl.almejo.vsim.ui.labels;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.sticky.StickyNotesExtension;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JToggleButton;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The «Texto» tool in an editor view: button, menu item, and the whole flow through the inline editor. */
class TextToolExtensionTest {

    private EditorFixture fixture;
    private EditorView view;
    private Runnable uninstallText;
    private Runnable uninstallSticky;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        view = fixture.view();
        // as in Application: the text tool before the sticky notes
        uninstallText = new TextToolExtension().install(view);
        uninstallSticky = new StickyNotesExtension((parent, firstLine) -> true).install(view);
    }

    @AfterEach
    void close() {
        uninstallSticky.run();
        uninstallText.run();
        fixture.close();
    }

    @Test
    void theToolComesRightAfterNotaAdhesivaInTheToolbarAndTheViewMenu() {
        List<String> names = new ArrayList<>();
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof JToggleButton button) {
                names.add(button.getAction().getValue(javax.swing.Action.NAME).toString());
            }
        }
        assertThat(names).containsExactly("Selección", "Cable", "Nota adhesiva", "Texto", "Mover vista", "Zoom");
        assertThat(texts(view.menu(MenuId.VIEW)).subList(0, 6))
                .containsExactly("Selección", "Cable", "Nota adhesiva", "Texto", "Mover vista", "Zoom");
        assertThat(view.shortcutActions()).containsKey("view.textTool");
    }

    @Test
    void aClickThenTypingAddsOneTextAsOneUndoStepAndTheToolIsBackToSeleccion() {
        perform(view.toolButton(TextTool.class).orElseThrow().getAction());
        assertThat(view.canvas().tool()).isInstanceOf(TextTool.class);

        TextTool tool = (TextTool) view.canvas().tool();
        tool.mousePressed(view.canvas(), TestToolContext.at(5, 6));
        tool.mouseReleased(view.canvas(), TestToolContext.at(5, 6));

        assertThat(view.canvas().tool()).isSameAs(view.canvas().defaultTool());
        view.inlineEditor().textComponent().setText("hola");
        view.inlineEditor().confirm();

        assertThat(fixture.openDocument().document().labels()).singleElement().satisfies(label -> {
            assertThat(label.text()).isEqualTo("hola");
            assertThat(label.position()).isEqualTo(new GridPoint(5, 6));
        });
        fixture.openDocument().history().undo();
        assertThat(fixture.openDocument().document().labels()).isEmpty();
    }

    @Test
    void blankTextAddsNothingAndLeavesSeleccionActive() {
        perform(view.toolButton(TextTool.class).orElseThrow().getAction());
        TextTool tool = (TextTool) view.canvas().tool();
        tool.mousePressed(view.canvas(), TestToolContext.at(5, 6));
        tool.mouseReleased(view.canvas(), TestToolContext.at(5, 6));
        view.inlineEditor().textComponent().setText("   ");
        view.inlineEditor().confirm();

        assertThat(fixture.openDocument().document().labels()).isEmpty();
        assertThat(fixture.openDocument().history().canUndo()).isFalse();
        assertThat(view.canvas().tool()).isSameAs(view.canvas().defaultTool());
    }
}
