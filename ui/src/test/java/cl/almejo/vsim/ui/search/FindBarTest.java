package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ctrl+F finds in the circuit only (the user's decision, round C of the «Pestañas» hand test): the «Ir a:» row that
 * also offered tabs and components is gone, since the circuit tree's search reaches every piece. Typing a component's
 * name never enters it, with Enter or Ctrl+Enter.
 */
class FindBarTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> entered = new ArrayList<>();
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public void enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                          Optional<ViewState> from) {
            entered.add(definitionId);
        }
    };
    private EditorView view;
    private FindFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.byteBuffer())));
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of()), window);
        features = new FindFeatures(view, () -> null, component -> { });
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private void open(String text) {
        features.findAction().actionPerformed(null);
        features.bar().field().setText(text);
    }

    private void press(int keyCode, int modifiers) {
        SearchField field = features.bar().field();
        field.deliver(new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void theBarOffersNoComponentsToGoTo() {
        open("inversor");

        assertThat(texts(features.bar())).doesNotContain("Ir a:")
                .noneMatch(text -> text.contains("componente"));
    }

    @Test
    void enterWithNoMatchInTheCircuitEntersNothing() {
        open("bufer");
        assertThat(features.hits()).isEmpty();

        press(KeyEvent.VK_ENTER, 0);

        assertThat(entered).isEmpty();
        assertThat(features.isOpen()).isTrue();
    }

    @Test
    void ctrlEnterEntersNothing() {
        open("inversor");
        assertThat(features.hits()).isNotEmpty();

        press(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);

        assertThat(entered).isEmpty();
    }

    /** Every label's and button's text in {@code root}, visible or not. */
    private static List<String> texts(Container root) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                texts.add(label.getText());
            } else if (child instanceof AbstractButton button && button.getText() != null) {
                texts.add(button.getText());
            }
            if (child instanceof Container container) {
                texts.addAll(texts(container));
            }
        }
        return texts;
    }
}
