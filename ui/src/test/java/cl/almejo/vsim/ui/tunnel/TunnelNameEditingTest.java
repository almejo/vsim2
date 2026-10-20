package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.actions.LabelActions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.ListModel;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The name of a tunnel is edited in place (spec §7): validation, suggestions and the rename that carries salidas. */
class TunnelNameEditingTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;
    private InlineTextEditor editor;
    private LabelActions actions;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        view.canvas().setSize(800, 600);
        editor = view.inlineEditor();
        actions = view.labelFeatures().actions();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private Component entrance(String name, int y) {
        return place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 0, y, Map.of(Tunnels.NAME, name));
    }

    private Component exit(String name, int y) {
        return place(openDocument, StandardComponentTypes.TUNNEL_EXIT, 20, y, Map.of(Tunnels.NAME, name));
    }

    private String nameOf(Component tunnel) {
        return Tunnels.nameOf(openDocument.document().component(tunnel.id()).orElseThrow());
    }

    private List<String> suggestions() {
        ListModel<String> model = editor.suggestionList().getModel();
        List<String> shown = new ArrayList<>();
        for (int index = 0; index < model.getSize(); index++) {
            shown.add(model.getElementAt(index));
        }
        return editor.suggestionList().isVisible() ? shown : List.of();
    }

    @Test
    void theValueEditOfATunnelOpensItsNameOverTheTag() {
        Component exit = exit("CLK", 0);

        actions.editValue(exit);

        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.field().getText()).isEqualTo("CLK");
        assertThat(editor.field().getSelectedText()).isEqualTo("CLK");
    }

    @Test
    void f2OnASelectedTunnelEditsItsName() {
        Component entrance = entrance("CLK", 0);
        view.canvas().selection().select(entrance.id());

        actions.editLabel().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));

        assertThat(editor.field().getText()).isEqualTo("CLK");
    }

    @Test
    void aNameMustNotBeBlankNorStartOrEndWithSpaces() {
        actions.editValue(entrance("CLK", 0));

        editor.field().setText("  ");
        assertThat(editor.problemText().getText()).isEqualTo(Messages.text("tunnel.name.blank"));
        editor.field().setText(" CLK");
        assertThat(editor.problemText().getText()).isEqualTo(Messages.text("tunnel.name.spaces"));
        editor.field().setText("CLK 2");
        assertThat(editor.problemText().isVisible()).isFalse();
    }

    @Test
    void renamingAnEntradaRenamesItsSalidasInOneUndoStep() {
        Component entrance = entrance("CLK", 0);
        List<Component> exits = List.of(exit("CLK", 0), exit("CLK", 10), exit("CLK", 20));
        Component other = exit("SEL", 30);

        actions.editValue(entrance);
        editor.field().setText("RELOJ");
        editor.confirm();

        assertThat(nameOf(entrance)).isEqualTo("RELOJ");
        exits.forEach(exit -> assertThat(nameOf(exit)).isEqualTo("RELOJ"));
        assertThat(nameOf(other)).isEqualTo("SEL");
        assertThat(openDocument.history().undoDescription()).contains("command.renameTunnel");
        openDocument.history().undo();
        assertThat(nameOf(entrance)).isEqualTo("CLK");
        exits.forEach(exit -> assertThat(nameOf(exit)).isEqualTo("CLK"));
    }

    @Test
    void theSameNameAddsNoStep() {
        actions.editValue(entrance("CLK", 0));
        editor.confirm();

        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void renamingASalidaRelinksOnlyThatOne() {
        entrance("CLK", 0);
        entrance("SEL", 10);
        Component first = exit("CLK", 0);
        Component second = exit("CLK", 10);

        actions.editValue(first);
        editor.field().setText("SEL");
        editor.confirm();

        assertThat(nameOf(first)).isEqualTo("SEL");
        assertThat(nameOf(second)).isEqualTo("CLK");
    }

    @Test
    void aSalidaSuggestsTheEntradaNamesAndFiltersThemAsTheUserTypes() {
        entrance("CLK", 0);
        entrance("CARRY", 10);
        entrance("SEL", 20);
        Component exit = exit("SEL", 0);

        actions.editValue(exit);
        assertThat(suggestions()).containsExactly("CARRY", "CLK");

        editor.field().setText("C");
        assertThat(suggestions()).containsExactly("CARRY", "CLK");
        editor.field().setText("cl");
        assertThat(suggestions()).containsExactly("CLK");
        editor.field().setText("X");
        assertThat(suggestions()).isEmpty();
    }

    @Test
    void choosingASuggestionRenamesTheSalida() {
        entrance("CLK", 0);
        Component exit = exit("T1", 0);

        actions.editValue(exit);
        editor.chooseSuggestion("CLK");

        assertThat(editor.isOpen()).isFalse();
        assertThat(nameOf(exit)).isEqualTo("CLK");
    }

    @Test
    void anEntradaSuggestsNothing() {
        entrance("CLK", 0);
        actions.editValue(entrance("SEL", 10));

        assertThat(suggestions()).isEmpty();
    }

    @Test
    void aRenamedEntradaIsTheNameTheNextSalidaProposes() {
        Component entrance = entrance("CLK", 0);

        actions.editValue(entrance);
        editor.field().setText("RELOJ");
        editor.confirm();

        assertThat(openDocument.lastTunnelEntranceName()).contains("RELOJ");
    }
}
