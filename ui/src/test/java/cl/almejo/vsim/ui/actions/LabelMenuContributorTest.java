package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.SetLabelOffsetCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class LabelMenuContributorTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;
    private Selection selection;
    private Point2D clickedAt = new Point2D.Double(7.8, 0.2);
    private LabelMenuContributor contributor;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        view.canvas().setSize(800, 600);
        selection = view.canvas().selection();
        contributor = new LabelMenuContributor(view.labelFeatures().actions());
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private JPopupMenu menuFor(Optional<DocumentElement> target) {
        JPopupMenu menu = new JPopupMenu();
        contributor.contribute(menu, target, selection, clickedAt);
        return menu;
    }

    private static void choose(JPopupMenu menu, int index) {
        JMenuItem item = (JMenuItem) menu.getComponent(index);
        item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void componentGetsLabelItem() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        selection.select(gate.id());
        JPopupMenu menu = menuFor(Optional.of(gate));
        assertThat(texts(menu)).containsExactly("Etiqueta…");
        choose(menu, 0);
        view.inlineEditor().field().setText("U9");
        view.inlineEditor().confirm();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().label()).isEqualTo("U9");
    }

    @Test
    void aComponentWithAMovedLabelGetsTheResetItemThatPutsItBackInOneStep() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        openDocument.execute(new SetLabelOffsetCommand(gate.id(), Optional.of(new LabelOffset(2, 6))));
        Component moved = openDocument.document().component(gate.id()).orElseThrow();
        selection.select(gate.id());
        JPopupMenu menu = menuFor(Optional.of(moved));
        assertThat(texts(menu)).containsExactly("Etiqueta…", "Restablecer posición de la etiqueta");
        choose(menu, 1);
        assertThat(openDocument.document().component(gate.id()).orElseThrow().labelOffset()).isEmpty();
        assertThat(openDocument.history().undoDescription()).contains("command.resetLabelPosition");
        openDocument.history().undo();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().labelOffset())
                .contains(new LabelOffset(2, 6));
    }

    @Test
    void aMultiSelectionGetsNoResetItem() {
        Component first = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 10, 0, Map.of());
        openDocument.execute(new SetLabelOffsetCommand(first.id(), Optional.of(new LabelOffset(2, 6))));
        selection.set(List.of(first.id(), second.id()));
        assertThat(texts(menuFor(openDocument.document().component(first.id()).map(DocumentElement.class::cast))))
                .isEmpty();
    }

    @Test
    void freeTextGetsEditTextItem() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "nota");
        openDocument.execute(new AddElementsCommand(List.of(label)));
        selection.select(label.id());
        assertThat(texts(menuFor(Optional.of(label)))).containsExactly("Editar texto…", "Estilo");
    }

    @Test
    void estiloOffersFondoLetraAndBordeEachWithItsColoursAndTheCurrentOneChosen() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "nota",
                new NoteStyle(NoteColor.NONE, NoteColor.RED, NoteColor.NONE));
        openDocument.execute(new AddElementsCommand(List.of(label)));
        selection.select(label.id());

        JMenu style = (JMenu) menuFor(Optional.of(label)).getComponent(1);

        assertThat(texts(style)).containsExactly("Fondo", "Letra", "Borde");
        JMenu text = (JMenu) style.getMenuComponent(1);
        assertThat(texts(text)).hasSize(NoteColor.values().length).startsWith("Automático", "Azul");
        assertThat(((JMenuItem) text.getMenuComponent(NoteColor.RED.ordinal())).isSelected()).isTrue();
        assertThat(texts((JMenu) style.getMenuComponent(0))).startsWith("Ninguno");
    }

    @Test
    void aColourFromEstiloPaintsEverySelectedNoteAsOneUndoStep() {
        TextLabel first = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "uno");
        TextLabel second = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 9), "dos");
        openDocument.execute(new AddElementsCommand(List.of(first, second)));
        selection.set(List.of(first.id(), second.id()));

        JPopupMenu menu = menuFor(Optional.of(first));
        assertThat(texts(menu)).containsExactly("Estilo");
        JMenu background = (JMenu) ((JMenu) menu.getComponent(0)).getMenuComponent(0);
        ((JMenuItem) background.getMenuComponent(NoteColor.YELLOW.ordinal())).doClick();

        assertThat(openDocument.document().labels()).extracting(note -> note.style().background())
                .containsExactly(NoteColor.YELLOW, NoteColor.YELLOW);
        openDocument.history().undo();
        assertThat(openDocument.document().labels()).extracting(TextLabel::style)
                .containsOnly(NoteStyle.PLAIN);
    }

    @Test
    void multiSelectionGetsNoLabelItems() {
        Component first = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 10, 0, Map.of());
        selection.set(List.of(first.id(), second.id()));
        assertThat(texts(menuFor(Optional.of(first)))).isEmpty();
    }

    @Test
    void wireGetsRemoveSegmentAtTheClickedPoint() {
        Wire main = new Wire(openDocument.document().newWireId(), new GridPoint(0, 0), new GridPoint(10, 0));
        openDocument.execute(new AddWireCommand(main));
        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(),
                new GridPoint(5, 0), new GridPoint(5, 4))));
        selection.select(main.id());
        JPopupMenu menu = menuFor(Optional.of(main));
        assertThat(texts(menu)).containsExactly("Borrar tramo");
        choose(menu, 0);
        assertThat(openDocument.document().wire(main.id()).orElseThrow().end()).isEqualTo(new GridPoint(5, 0));
    }

    @Test
    void emptyCanvasGetsAddTextHere() {
        clickedAt = new Point2D.Double(4.4, 6.6);
        JPopupMenu menu = menuFor(Optional.empty());
        assertThat(texts(menu)).containsExactly("Agregar texto aquí");
        choose(menu, 0);
        view.inlineEditor().textComponent().setText("nota");
        view.inlineEditor().confirm();
        assertThat(openDocument.document().labels()).singleElement()
                .satisfies(label -> assertThat(label.position()).isEqualTo(new GridPoint(4, 7)));
    }
}
