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
import cl.almejo.vsim.ui.window.WindowMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** «Ir a…» (spec §8.3): the find bar also offers the open tabs and the circuit's components. */
class FindBarTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> entered = new ArrayList<>();
    private final List<String> focusedTabs = new ArrayList<>();
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
            entered.add(definitionId + "@" + contextPath.orElse("-"));
        }

        @Override
        public List<WindowMenu.Entry> tabEntries() {
            return List.of(new WindowMenu.Entry("lrv32im", true, () -> focusedTabs.add("lrv32im")),
                    new WindowMenu.Entry("⬚ Inversor", false, () -> focusedTabs.add("⬚ Inversor")));
        }
    };
    private EditorView view;
    private FindFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter(), ExampleDefinitions.byteBuffer())));
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30, 0, Map.of());
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

    private List<String> goToTexts() {
        return features.bar().goToButtons().stream().map(JButton::getText).toList();
    }

    private void press(int keyCode, int modifiers) {
        SearchField field = features.bar().field();
        field.deliver(new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void listsTabsAndComponents() {
        open("inversor");

        // accent- and case-insensitive; tabs first; use counts: the circuit's inverter plus the two in the double one
        assertThat(goToTexts()).containsExactly("⬚ Inversor", "⬚ Inversor — componente, 3 usos",
                "⬚ Doble inversor — componente, 1 uso");
    }

    @Test
    void noGoToEntriesWithoutTextOrMatch() {
        open("");
        assertThat(goToTexts()).isEmpty();
        features.bar().field().setText("zzzz");
        assertThat(goToTexts()).isEmpty();
        assertThat(features.bar().goToRow().isVisible()).isFalse();
    }

    @Test
    void anUnusedComponentSaysSo() {
        open("bufer");

        assertThat(goToTexts()).containsExactly("⬚ Búfer de 8 bits — componente, sin usos");
    }

    @Test
    void choosingAComponentEnters() {
        open("doble");

        features.bar().goToButtons().getFirst().doClick();

        assertThat(entered).hasSize(1);
        assertThat(entered.getFirst()).startsWith("double-inverter@c");
        assertThat(features.isOpen()).isFalse();
    }

    @Test
    void choosingATabActivatesIt() {
        open("lrv");

        features.bar().goToButtons().getFirst().doClick();

        assertThat(focusedTabs).containsExactly("lrv32im");
        assertThat(entered).isEmpty();
    }

    @Test
    void anUnusedComponentEntersWithoutValues() {
        open("bufer");

        features.bar().goToButtons().getFirst().doClick();

        assertThat(entered).containsExactly("byte-buffer@-");
    }

    @Test
    void enterChoosesTheFirstEntryWhenNothingMatchesTheCircuit() {
        open("bufer");
        assertThat(features.hits()).isEmpty();

        press(KeyEvent.VK_ENTER, 0);

        assertThat(entered).containsExactly("byte-buffer@-");
    }

    @Test
    void ctrlEnterChoosesTheFirstEntryEvenWithMatches() {
        open("inversor");
        assertThat(features.hits()).isNotEmpty();

        press(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);

        assertThat(focusedTabs).containsExactly("⬚ Inversor");
    }
}
