package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Container;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** What every user component's piece offers in Propiedades and its right-click menu. */
class UserPieceShortcutsTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Component createNegador() {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));
        perform(fixture.features.actions().createComponent());
        return fixture.single("subcircuit:negador");
    }

    private static PropertiesPanel propertiesOf(EditorView view) {
        return view.labelFeatures().properties();
    }

    private static void collect(Container container, List<AbstractButton> buttons, List<String> labels) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof AbstractButton button && button.getText() != null) {
                buttons.add(button);
            }
            if (child instanceof JLabel label && label.getText() != null) {
                labels.add(label.getText());
            }
            if (child instanceof Container inner) {
                collect(inner, buttons, labels);
            }
        }
    }

    private static List<String> buttonTexts(EditorView view) {
        List<AbstractButton> buttons = new ArrayList<>();
        collect(propertiesOf(view), buttons, new ArrayList<>());
        return buttons.stream().map(AbstractButton::getText).toList();
    }

    private JPopupMenu menuOf(Component piece) {
        return fixture.view.contextMenu(Optional.of(piece), new Point2D.Double());
    }

    private static Optional<JMenuItem> item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    @Test
    void propertiesOfAUserPieceOffersEntrarAndEndsWithTheDescription() {
        Component piece = createNegador();
        fixture.view.canvas().selection().select(piece.id());

        List<String> texts = buttonTexts(fixture.view);

        assertThat(texts).contains(Messages.text("properties.enter"), Messages.text("action.editDescription"));
        assertThat(texts.get(texts.size() - 1)).isEqualTo(Messages.text("action.editDescription"));
        assertThat(texts.indexOf(Messages.text("properties.enter")))
                .isLessThan(texts.indexOf(Messages.text("action.editDescription")));
    }

    @Test
    void anOrdinaryComponentHasNeitherEntrarNorTheDescription() {
        Component gate = fixture.drawInverter(0, 0).get(0);
        fixture.view.canvas().selection().select(gate.id());

        assertThat(buttonTexts(fixture.view)).doesNotContain(Messages.text("properties.enter"),
                Messages.text("action.editDescription"));
    }

    @Test
    void theEntrarButtonEntersThePiece() {
        Component piece = createNegador();
        List<String> entered = new ArrayList<>();
        WindowCommands recording = new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
            @Override public void enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                                        Optional<ViewState> from) {
                entered.add(definitionId);
            }
        };
        EditorView view = new EditorView(fixture.openDocument, fixture.services(), recording);
        TemplateFeatures features = TemplateFeatures.install(view, fixture.service, fixture.dialogs);
        try {
            view.canvas().selection().select(piece.id());
            List<AbstractButton> buttons = new ArrayList<>();
            collect(propertiesOf(view), buttons, new ArrayList<>());

            buttons.stream().filter(button -> Messages.text("properties.enter").equals(button.getText()))
                    .findFirst().orElseThrow().doClick();

            assertThat(entered).containsExactly("negador");
        } finally {
            features.dispose();
            view.dispose();
        }
    }

    @Test
    void rowsKeepTheirPlaceWhateverTheOrderTheyWereAdded() {
        propertiesOf(fixture.view).addComponentRows(new Marker("last", PropertiesPanel.RowsPlace.LAST));
        propertiesOf(fixture.view).addComponentRows(new Marker("after", PropertiesPanel.RowsPlace.AFTER_PARAMETERS));
        propertiesOf(fixture.view).addComponentRows(new Marker("before", PropertiesPanel.RowsPlace.BEFORE_LABEL));
        Component gate = fixture.drawInverter(0, 0).get(0);
        fixture.view.canvas().selection().select(gate.id());

        List<String> labels = new ArrayList<>();
        collect(propertiesOf(fixture.view), new ArrayList<>(), labels);

        assertThat(labels.indexOf("before")).isNotNegative()
                .isLessThan(labels.indexOf(Messages.text("properties.label")));
        assertThat(labels.indexOf("after")).isGreaterThan(labels.indexOf(Messages.text("properties.label")));
        assertThat(labels.indexOf("last")).isGreaterThan(labels.indexOf("after"));
    }

    private record Marker(String caption, PropertiesPanel.RowsPlace place) implements PropertiesPanel.ComponentRows {
        @Override
        public void addRows(Component component, PropertiesPanel.Rows rows) {
            rows.add(caption, new JLabel("x"));
        }
    }

    @Test
    void theMenuOfAPieceNotInMisComponentsOffersAddingItAndDoesIt() {
        Component piece = createNegador();
        fixture.library.remove("negador");
        fixture.select(piece);

        JMenuItem entry = item(menuOf(piece), Messages.text("action.addToLibrary")).orElseThrow();
        assertThat(entry.isEnabled()).isTrue();
        entry.doClick();

        assertThat(fixture.library.find("negador")).isPresent();
        JMenuItem after = item(menuOf(piece), Messages.text("action.addToLibrary")).orElseThrow();
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.getToolTipText()).isEqualTo(Messages.text("action.addToLibrary.already"));
    }

    @Test
    void theMenuOfAnOrdinaryComponentDoesNotOfferIt() {
        Component gate = fixture.drawInverter(0, 0).get(0);
        fixture.select(gate);

        assertThat(item(menuOf(gate), Messages.text("action.addToLibrary"))).isEmpty();
    }
}
