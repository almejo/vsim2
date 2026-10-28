package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The memory rows of the «Propiedades» tab: where the contents live, the editors and the hidden-words warning. */
class MemoryInspectorRowsTest {

    @TempDir
    Path folder;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<String> launched = new ArrayList<>();
    private final FakeLinkPrompts prompts = new FakeLinkPrompts();
    private final List<Path> externallyOpened = new ArrayList<>();
    private final MemoryLinks links = new MemoryLinks(prompts, (file, command) -> externallyOpened.add(file),
            MemoryLinks.PollScheduler.MANUAL);
    private final MemoryEditorLauncher launcher = new MemoryEditorLauncher(links) {
        @Override
        public void open(EditorView view, ElementId componentId, OptionalLong address) {
            launched.add(componentId.value() + address);
        }
    };
    private EditorView view;

    @AfterEach
    void close() {
        if (view != null) {
            view.dispose();
        }
        document.close();
    }

    private Container inspectorShowing(Component component) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new MemoryEditorExtension(launcher, links)));
        view = new EditorView(document, services, NO_WINDOW);
        view.selection().select(component.id());
        return view.labelFeatures().properties();
    }

    private static <T extends java.awt.Component> T named(Container root, String name, Class<T> kind) {
        for (java.awt.Component child : root.getComponents()) {
            if (kind.isInstance(child) && name.equals(child.getName())) {
                return kind.cast(child);
            }
            if (child instanceof Container container) {
                T nested = named(container, name, kind);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String words(Component component) {
        return (String) document.document().component(component.id()).orElseThrow().parameters()
                .get(MemoryTypes.WORDS);
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }

    private static void collectOutside(Container root, Container container, int width, List<String> outside) {
        for (java.awt.Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue; // nothing inside a hidden part shows (the buttons of a scroll bar not needed now)
            }
            java.awt.Rectangle inRoot = javax.swing.SwingUtilities.convertRectangle(container, child.getBounds(), root);
            if (inRoot.x + inRoot.width > width || (child instanceof javax.swing.JComponent && child.isVisible() && inRoot.width <= 0 && !(child instanceof JLabel label && label.getText().isEmpty()))) {
                outside.add(child.getClass().getSimpleName() + " " + child.getName() + " " + inRoot);
            }
            if (child instanceof Container nested) {
                collectOutside(root, nested, width, outside);
            }
        }
    }

    @Test
    void theMemoryRowsFitTheDefaultSidebarWidth() {
        for (String type : List.of(StandardComponentTypes.RAM, StandardComponentTypes.ROM)) {
            Component memory = place(document, type, 0, 0, Map.of());
            Container inspector = inspectorShowing(memory);
            int width = cl.almejo.vsim.ui.window.InspectorPanel.PREFERRED_WIDTH;
            inspector.setSize(width, 900);
            layOut(inspector);

            List<String> outside = new ArrayList<>();
            collectOutside(inspector, inspector, width, outside);
            assertThat(outside).as(type).isEmpty();
            assertThat(inspector.getMinimumSize().width).as(type).isLessThanOrEqualTo(width);
            for (String parameter : List.of(MemoryTypes.WIDTH, "delay")) {
                assertThat(named(inspector, parameter, javax.swing.JComponent.class).getWidth())
                        .as(type + " " + parameter).isGreaterThan(20);
            }
            view.dispose();
            view = null;
        }
    }

    @Test
    void theWarningsAndTheirButtonsFitTheDefaultSidebarWidthToo() {
        MemoryContents contents = MemoryContents.empty().with(0, 1).with(299, 0x3FF);
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.WORDS, "256", MemoryTypes.CONTENTS, contents.encode()));
        Container inspector = inspectorShowing(rom);
        int width = cl.almejo.vsim.ui.window.InspectorPanel.PREFERRED_WIDTH;
        inspector.setSize(width, 900);
        layOut(inspector);

        List<String> outside = new ArrayList<>();
        collectOutside(inspector, inspector, width, outside);
        assertThat(outside).isEmpty();
        assertThat(named(inspector, "memory.inspector.hidden", javax.swing.text.JTextComponent.class)).isNotNull();
    }

    @Test
    void aMemoryInTheCircuitSaysSoAndOpensItsEditor() {
        Component ram = place(document, StandardComponentTypes.RAM, 0, 0, Map.of());
        Container inspector = inspectorShowing(ram);

        assertThat(named(inspector, "memory.inspector.contents", JLabel.class).getText()).isEqualTo("En el circuito");
        assertThat(named(inspector, "memory.inspector.external", AbstractButton.class).isEnabled()).isFalse();
        named(inspector, "memory.inspector.edit", AbstractButton.class).doClick();
        assertThat(launched).containsExactly(ram.id().value() + OptionalLong.empty());
        assertThat(named(inspector, "memory.inspector.hidden", javax.swing.text.JTextComponent.class)).isNull();
    }

    @Test
    void aLinkedMemoryShowsItsFileAndOpensItInTheExternalEditor() throws Exception {
        Path file = folder.resolve("programas/rom.hex");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "01\n");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.CONTENTS, "@0 1", MemoryTypes.LINK, file.toString()));
        document.save(folder.resolve("cpu.vsim"));
        Container inspector = inspectorShowing(rom);

        assertThat(named(inspector, "memory.inspector.contents", JLabel.class).getText())
                .isEqualTo("Enlazado: programas/rom.hex");
        named(inspector, "memory.inspector.external", AbstractButton.class).doClick();
        assertThat(externallyOpened).containsExactly(file);
    }

    @Test
    void aMissingLinkedFileIsShownInTheInspector() throws IOException {
        Path file = folder.resolve("rom.hex");
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.CONTENTS, "@0 1", MemoryTypes.LINK, file.toString()));
        Container inspector = inspectorShowing(rom);
        links.watcherOf(document).orElseThrow().poll();

        assertThat(named(inspector, "memory.inspector.problem", javax.swing.text.JTextComponent.class).getText()).startsWith("No se encuentra");
    }

    @Test
    void contentsBeyondTheLastWordWarnAndGrowToTheNextPowerInOneStep() {
        MemoryContents contents = MemoryContents.empty().with(0, 1).with(299, 0xFF);
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.WORDS, "256", MemoryTypes.CONTENTS, contents.encode()));
        Container inspector = inspectorShowing(rom);

        assertThat(named(inspector, "memory.inspector.hidden", javax.swing.text.JTextComponent.class).getText())
                .isEqualTo("⚠ El contenido tiene 300 palabras y la memoria 256. Las 44 últimas no se ven.");
        AbstractButton grow = named(inspector, "memory.inspector.grow", AbstractButton.class);
        assertThat(grow.getText()).isEqualTo("Agrandar a 512");
        long changes = document.changeCount();
        grow.doClick();
        assertThat(words(rom)).isEqualTo("512");
        assertThat(document.changeCount()).isEqualTo(changes + 1);
        assertThat(named(view.labelFeatures().properties(), "memory.inspector.hidden", javax.swing.text.JTextComponent.class)).isNull();
        document.history().undo();
        assertThat(words(rom)).isEqualTo("256");
    }

    @Test
    void seeingTheHiddenWordsOpensTheEditorAtTheLastAddress() {
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, MemoryContents.empty().with(20, 1).encode()));
        named(inspectorShowing(rom), "memory.inspector.view", AbstractButton.class).doClick();
        assertThat(launched).containsExactly(rom.id().value() + OptionalLong.of(15));
    }

    @Test
    void valuesWiderThanTheWordWarnAndWidenInOneStep() {
        Component rom = place(document, StandardComponentTypes.ROM, 0, 0,
                Map.of(MemoryTypes.WIDTH, 8, MemoryTypes.CONTENTS, MemoryContents.empty().with(0, 0x3FF).encode()));
        Container inspector = inspectorShowing(rom);

        assertThat(named(inspector, "memory.inspector.hiddenBits", javax.swing.text.JTextComponent.class).getText())
                .isEqualTo("⚠ El contenido tiene valores de 10 bits y la palabra es de 8: los bits de más no se ven.");
        named(inspector, "memory.inspector.widen", AbstractButton.class).doClick();
        assertThat(document.document().component(rom.id()).orElseThrow().parameters().get(MemoryTypes.WIDTH))
                .isEqualTo(10);
    }

    @Test
    void theNextPowerNeverPassesTheLargestMemory() {
        assertThat(MemoryInspectorRows.wordsToShow(MemoryContents.empty().with(299, 1))).isEqualTo(512);
        assertThat(MemoryInspectorRows.wordsToShow(MemoryContents.empty().with(1, 1))).isEqualTo(2);
        assertThat(MemoryInspectorRows.wordsToShow(MemoryContents.empty().with(20_000_000L, 1)))
                .isEqualTo(16_777_216L);
    }
}
