package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.ZoomTool;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class ExportImageExtensionTest {

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> messages = new ArrayList<>();
    private final List<String> chooserCalls = new ArrayList<>();
    private Optional<ExportChoice> answer = Optional.empty();
    private OpenDocument openDocument;
    private EditorView view;
    private ExportImageFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        features = new ExportImageFeatures(view, (parent, startDirectory, suggestedName, scale, printBackground) -> {
            chooserCalls.add(startDirectory + "|" + suggestedName + "|" + scale + "|" + printBackground);
            return answer;
        }, (parent, message) -> messages.add(message));
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    @Test
    void theItemSitsRightAfterSaveAsAndIsDisabledOnAnEmptyDocument() {
        List<String> texts = texts(view.menu(MenuId.FILE));
        assertThat(texts.indexOf("Exportar imagen…")).isEqualTo(texts.indexOf("Guardar como…") + 1);
        assertThat(features.action().isEnabled()).isFalse();
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        assertThat(features.action().isEnabled()).isTrue();
        openDocument.history().undo();
        assertThat(features.action().isEnabled()).isFalse();
    }

    @Test
    void aPngAtTwoTimesIsWrittenWithThatSize() throws Exception {
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Path file = directory.resolve("circuit.png");
        answer = Optional.of(new ExportChoice(file, ExportChoice.Format.PNG, 2, true));
        perform(features.action());
        CircuitImageFixture expected = CircuitImageFixture.of(openDocument, Optional.empty());
        BufferedImage decoded = ImageIO.read(file.toFile());
        assertThat(decoded.getWidth()).isEqualTo(expected.width(2));
        assertThat(decoded.getHeight()).isEqualTo(expected.height(2));
        assertThat(decoded.getRGB(0, 0)).as("white background for printing").isEqualTo(0xFFFFFFFF);
        assertThat(messages).isEmpty();
    }

    @Test
    void anSvgChoiceWritesSvg() throws Exception {
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Path file = directory.resolve("circuit.svg");
        answer = Optional.of(new ExportChoice(file, ExportChoice.Format.SVG, 2, false));
        perform(features.action());
        assertThat(Files.readString(file)).contains("<svg");
    }

    @Test
    void theZoomToolsRectangleNeverReachesAnExportedImage() throws Exception {
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(800, 600);
        view.canvasPanes().doLayout();
        Path plain = directory.resolve("plain.png");
        answer = Optional.of(new ExportChoice(plain, ExportChoice.Format.PNG, 1, false));
        perform(features.action());

        view.actions().zoomTool().actionPerformed(null);
        CircuitCanvas canvas = view.canvas();
        assertThat(canvas.tool()).isInstanceOf(ZoomTool.class);
        int down = InputEvent.BUTTON1_DOWN_MASK;
        canvas.getMouseListeners()[0].mousePressed(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, down, 50, 50, 1,
                false, MouseEvent.BUTTON1));
        canvas.getMouseMotionListeners()[0].mouseDragged(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, down,
                400, 300, 1, false, MouseEvent.NOBUTTON));
        assertThat(canvas.tool().animatesOutline()).as("a rectangle is being dragged").isTrue();
        Path during = directory.resolve("during.png");
        answer = Optional.of(new ExportChoice(during, ExportChoice.Format.PNG, 1, false));
        perform(features.action());

        BufferedImage expected = ImageIO.read(plain.toFile());
        BufferedImage actual = ImageIO.read(during.toFile());
        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertThat(actual.getRGB(x, y)).as("pixel (%d, %d)", x, y).isEqualTo(expected.getRGB(x, y));
            }
        }
    }

    @Test
    void aSelectionExportsOnlyItsBounds() throws Exception {
        Component left = place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        place(openDocument, StandardComponentTypes.LED, 80, 10, Map.of());
        view.canvas().selection().set(Set.of(left.id()));
        Path file = directory.resolve("selection.png");
        answer = Optional.of(new ExportChoice(file, ExportChoice.Format.PNG, 1, true));
        perform(features.action());
        CircuitImageFixture expected = CircuitImageFixture.of(openDocument, Optional.of(Set.of(left.id())));
        CircuitImageFixture everything = CircuitImageFixture.of(openDocument, Optional.empty());
        BufferedImage decoded = ImageIO.read(file.toFile());
        assertThat(decoded.getWidth()).isEqualTo(expected.width(1)).isLessThan(everything.width(1));
    }

    @Test
    void theChoicesAreRememberedAndOfferedNextTime() {
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Path folder = directory.resolve("images");
        assertThat(folder.toFile().mkdirs()).isTrue();
        answer = Optional.of(new ExportChoice(folder.resolve("a.png"), ExportChoice.Format.PNG, 4, false));
        perform(features.action());
        assertThat(preferences.exportScale()).isEqualTo(4);
        assertThat(preferences.exportPrintBackground()).isFalse();
        assertThat(preferences.exportDirectory()).contains(folder);

        answer = Optional.empty();
        perform(features.action());
        assertThat(chooserCalls.get(1)).startsWith(folder + "|").endsWith("|4|false");
    }

    @Test
    void theDefaultsAreTwoTimesAndWhiteBackground() {
        assertThat(preferences.exportScale()).isEqualTo(2);
        assertThat(preferences.exportPrintBackground()).isTrue();
        assertThat(preferences.exportDirectory()).isEmpty();
    }

    @Test
    void cancellingWritesNothingAndChangesNoPreference() {
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        perform(features.action());
        assertThat(preferences.exportDirectory()).isEmpty();
        assertThat(messages).isEmpty();
    }

    @Test
    void aWriteFailureIsToldWithTheFileName() {
        place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Path missingFolder = directory.resolve("nope").resolve("x.png");
        answer = Optional.of(new ExportChoice(missingFolder, ExportChoice.Format.PNG, 1, true));
        perform(features.action());
        assertThat(messages).singleElement().asString().startsWith("No se pudo exportar x.png: ");
    }

    @Test
    void aPngTooLargeForMemoryIsRefusedWithTheFailureMessage() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 2000, 1000, Map.of());
        Path file = directory.resolve("huge.png");
        answer = Optional.of(new ExportChoice(file, ExportChoice.Format.PNG, 4, true));
        perform(features.action());
        assertThat(messages).singleElement().asString()
                .startsWith("No se pudo exportar huge.png: la imagen sería demasiado grande (");
        assertThat(file).doesNotExist();
    }

    @Test
    void theTypedExtensionWinsOverTheFilterAndOtherwiseTheFilterCompletesIt() {
        Path typed = directory.resolve("a.svg");
        assertThat(ExportChoice.of(typed, ExportChoice.Format.PNG, 1, true).format())
                .isEqualTo(ExportChoice.Format.SVG);
        assertThat(ExportChoice.of(typed, ExportChoice.Format.PNG, 1, true).path()).isEqualTo(typed);
        ExportChoice completed = ExportChoice.of(directory.resolve("a"), ExportChoice.Format.PNG, 1, true);
        assertThat(completed.path()).isEqualTo(directory.resolve("a.png"));
        assertThat(completed.format()).isEqualTo(ExportChoice.Format.PNG);
    }

    @Test
    void disposeTakesTheItemTheShortcutAndTheListenerBack() {
        EditorView bare = new EditorView(OpenDocument.createEmpty(StandardComponentTypes.registry()),
                new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                        new ComponentLibrary(), List.of()), NO_WINDOW);
        List<String> before = texts(bare.menu(MenuId.FILE));
        int listeners = bare.openDocument().changeListenerCount();
        ExportImageFeatures extra = new ExportImageFeatures(bare, (parent, start, name, scale, print) -> Optional.empty(),
                (parent, message) -> { });
        assertThat(bare.shortcutActions()).containsKey("file.exportImage");
        extra.dispose();
        assertThat(texts(bare.menu(MenuId.FILE))).isEqualTo(before);
        assertThat(bare.shortcutActions()).doesNotContainKey("file.exportImage");
        assertThat(bare.openDocument().changeListenerCount()).isEqualTo(listeners);
        bare.dispose();
        bare.openDocument().close();
    }
}
