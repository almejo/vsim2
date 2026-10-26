package cl.almejo.vsim.ui.examples;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.session.RecentFilesFeature;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class ExamplesFeatureTest {

    /** Records what the window was asked to open. */
    private final List<CircuitDocument> untitled = new ArrayList<>();
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public void openUntitled(CircuitDocument document) {
            untitled.add(document);
        }
    };
    private final List<String> failures = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT), window);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private ExamplesFeature feature() {
        return new ExamplesFeature(id -> BundledExamples.open(id, StandardComponentTypes.registry()),
                component -> Optional.of(window), (parent, name) -> failures.add(name));
    }

    private static JMenu examplesMenu(JMenu file) {
        for (Component child : file.getMenuComponents()) {
            if (child instanceof JMenu menu && "Ejemplos".equals(menu.getText())) {
                return menu;
            }
        }
        throw new AssertionError("no Ejemplos menu in " + texts(file));
    }

    private static JMenuItem item(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu));
    }

    @Test
    void theExamplesComeRightAfterRecientesInMenuOrderAndGoAwayOnDispose() {
        JMenu file = view.menu(MenuId.FILE);
        Runnable recentDisposer = new RecentFilesFeature().install(view);
        List<String> before = texts(file);
        Runnable disposer = feature().install(view);

        List<String> after = texts(file);
        assertThat(after.get(after.indexOf("Recientes") + 1)).isEqualTo("Ejemplos");
        assertThat(after).hasSize(before.size() + 1);
        JMenu examples = examplesMenu(file);
        assertThat(texts(examples)).containsExactly("Sumador completo", "Sumador de 4 bits", "Multiplexor 4:1",
                "Decodificador 2 a 4", "Paridad de 3 bits", "Multiplicador 2×2", "Registro de 4 bits",
                "Registro de desplazamiento", "Contador de 4 bits", "Contador Johnson", "Bus de 8 bits",
                "ALU de 4 bits", "Mini ALU", "Acumulador");
        assertThat(item(examples, "Sumador completo").getToolTipText())
                .isEqualTo("Suma A, B y el acarreo de entrada con dos XOR, dos AND y una OR");

        disposer.run();
        assertThat(texts(file)).isEqualTo(before);
        recentDisposer.run();
    }

    @Test
    void withoutRecientesTheExamplesFollowAbrir() {
        JMenu file = view.menu(MenuId.FILE);
        Runnable disposer = feature().install(view);
        List<String> texts = texts(file);
        assertThat(texts.get(texts.indexOf("Abrir…") + 1)).isEqualTo("Ejemplos");
        disposer.run();
    }

    @Test
    void everyExampleHasANameAndADescriptionInBothLanguages() {
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            ResourceBundle bundle = Messages.bundle(locale);
            assertThat(bundle.containsKey("menu.file.examples")).as("%s menu.file.examples", locale).isTrue();
            assertThat(bundle.containsKey("example.openFailed")).as("%s example.openFailed", locale).isTrue();
            for (String id : BundledExamples.ids()) {
                assertThat(bundle.getString("example." + id)).as("%s %s", locale, id).isNotBlank();
                assertThat(bundle.getString("example." + id + ".description")).as("%s %s", locale, id)
                        .isNotBlank();
            }
        }
    }

    @Test
    void choosingAnExampleOpensAFreshUntitledCopyNamedAfterTheExample() {
        Runnable disposer = feature().install(view);
        JMenuItem fullAdder = item(examplesMenu(view.menu(MenuId.FILE)), "Sumador completo");

        fullAdder.doClick(0);
        fullAdder.doClick(0);

        assertThat(untitled).hasSize(2);
        CircuitDocument first = untitled.get(0);
        assertThat(first.name()).isEqualTo("Sumador completo");
        assertThat(first.components()).isNotEmpty();
        assertThat(untitled.get(1)).as("each choice reads a new copy").isNotSameAs(first);
        assertThat(untitled.get(1).components()).isEqualTo(first.components());
        assertThat(failures).isEmpty();
        disposer.run();
    }

    @Test
    void theNameFollowsTheLanguage() {
        Messages.initialize(Locale.ENGLISH);
        try {
            feature().open("adder-4-bits", view);
            assertThat(untitled).singleElement().satisfies(document ->
                    assertThat(document.name()).isEqualTo("4-bit adder"));
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }

    @Test
    void anExampleThatCannotBeReadShowsTheFailureAndOpensNothing() {
        ExamplesFeature broken = new ExamplesFeature(id -> {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, id);
        }, component -> Optional.of(window), (parent, name) -> failures.add(name));
        Runnable disposer = broken.install(view);

        item(examplesMenu(view.menu(MenuId.FILE)), "Paridad de 3 bits").doClick(0);

        assertThat(failures).containsExactly("Paridad de 3 bits");
        assertThat(untitled).isEmpty();
        disposer.run();
    }

    @Test
    void outsideAWindowChoosingAnExampleDoesNothing() {
        List<Component> asked = new ArrayList<>();
        ExamplesFeature lonely = new ExamplesFeature(id -> BundledExamples.open(id, StandardComponentTypes.registry()),
                component -> {
                    asked.add(component);
                    return Optional.empty();
                }, (parent, name) -> failures.add(name));

        lonely.open("full-adder", view);

        assertThat(asked).as("the feature asks the lookup for the window of the view").containsExactly(view);
        assertThat(untitled).as("no window: nothing is opened").isEmpty();
        assertThat(failures).as("not a failure either").isEmpty();
        assertThat(ExamplesFeature.windowCommandsOf(view)).as("production lookup: a view in no window has none")
                .isEmpty();
    }
}
