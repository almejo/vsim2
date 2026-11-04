package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.ScaleMode;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The summary label above the first sheet (sheets spec §3.1 point 4, §5.5): what it says for each layout, and its
 * quick options popup, which writes the same remembered options the print dialog reads, redraws the sheets, never
 * touches the circuit and leads to the dialog with «Más opciones…».
 */
class SheetSummaryTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<JPopupMenu> shownMenus = new ArrayList<>();
    private final SheetsExtension extension = new SheetsExtension(new SheetAnchors(),
            (menu, invoker, x, y) -> shownMenus.add(menu));
    private OpenDocument openDocument;
    private EditorView view;
    private SheetsFeatures features;
    private CircuitCanvas pane;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        pane = view.canvas();
        pane.setSize(800, 600);
        features = extension.installFeatures(view);
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100)));
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 100, 0, Map.of());
        openDocument.history().markSaved();
        preferences.setShowSheets(true);
        pane.viewport().showAt(0.5, 50, 40, 800, 600);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private String summary() {
        return features.picture().orElseThrow().summary();
    }

    private SheetSummaryHandle handle() {
        return pane.edgeHandles().stream().filter(SheetSummaryHandle.class::isInstance)
                .map(SheetSummaryHandle.class::cast).findFirst().orElseThrow();
    }

    private void click(Rectangle box) {
        int x = (int) box.getCenterX();
        int y = (int) box.getCenterY();
        MouseEvent press = new MouseEvent(pane, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
        MouseEvent release = new MouseEvent(pane, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false,
                MouseEvent.BUTTON1);
        for (MouseListener listener : pane.getMouseListeners()) {
            listener.mousePressed(press);
        }
        for (MouseListener listener : pane.getMouseListeners()) {
            listener.mouseReleased(release);
        }
    }

    private JPopupMenu openPopup() {
        shownMenus.clear();
        click(handle().box().orElseThrow());
        assertThat(shownMenus).hasSize(1);
        return shownMenus.getFirst();
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu));
    }

    @Test
    void theSummaryNamesThePaperTheScaleAndTheSheets() {
        assertThat(summary()).isEqualTo("A4 vertical · 100 % · 2 × 1 = 2 hojas");

        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.LETTER, new ScaleMode.Percentage(25)));
        assertThat(summary()).isEqualTo("Carta vertical · 1 hoja · 25 %");

        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.FitToOnePage()));
        assertThat(summary()).matches("A4 vertical · Ajustar a una hoja \\(\\d+(,\\d)? %\\)");
    }

    @Test
    void skippingEmptySheetsTheSummarySaysHowManyArePrinted() {
        place(openDocument, StandardComponentTypes.LED, 300, 0, Map.of());
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100), true));
        int columns = features.picture().orElseThrow().grid().columns();
        assertThat(columns).as("the fixture: an empty sheet before the far lamp").isGreaterThan(3);
        String sheets = "A4 vertical · 100 % · " + columns + " × 1 = " + columns + " hojas";

        assertThat(summary()).matches(java.util.regex.Pattern.quote(sheets) + " · se imprimen [23]");

        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100), false));
        assertThat(summary()).isEqualTo(sheets);
    }

    @Test
    void theQuickOptionsSkipEmptySheetsLikeTheDialog() {
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100), true));
        JPopupMenu menu = openPopup();
        JMenuItem skip = item(menu, "Saltar hojas vacías");
        assertThat(skip).isInstanceOf(javax.swing.JCheckBoxMenuItem.class);
        assertThat(skip.isSelected()).isTrue();

        skip.doClick();

        assertThat(preferences.printOptions("").skipEmptySheets()).isFalse();
        assertThat(preferences.printOptions("").scaleMode()).isEqualTo(new ScaleMode.Percentage(100));
        assertThat(item(openPopup(), "Saltar hojas vacías").isSelected()).isFalse();
        assertThat(openDocument.isModified()).isFalse();
    }

    @Test
    void tooManySheetsForPrintingAreSaid() {
        place(openDocument, StandardComponentTypes.LED, 700, 900, Map.of());
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(400)));

        assertThat(summary()).endsWith(" · demasiadas para imprimir (máx. 1.000)");
    }

    @Test
    void theLabelSitsAboveTheFirstSheetWithAHandCursorAndATooltip() {
        Rectangle box = handle().box().orElseThrow();
        Rectangle firstSheet = overlay().drawing().borders().getFirst();

        assertThat(box.y + box.height).isLessThanOrEqualTo(firstSheet.y);
        assertThat(box.x).isBetween(firstSheet.x - 2, firstSheet.x + 8);
        assertThat(handle().cursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        assertThat(handle().tooltip()).isEqualTo("Cambiar papel, orientación o escala");
    }

    private SheetOverlay overlay() {
        return pane.overlays().stream().filter(SheetOverlay.class::isInstance).map(SheetOverlay.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    void aClickOnTheLabelOpensTheQuickOptionsAndDoesNotReachTheTool() {
        JPopupMenu menu = openPopup();

        assertThat(texts(menu)).containsExactly("A4 (21 × 29,7 cm)", "Carta (21,6 × 27,9 cm)",
                "Oficio (21,6 × 33 cm)", "A3 (29,7 × 42 cm)", "---", "Vertical", "Horizontal", "---",
                "Ajustar a una hoja", "100 %", "75 %", "50 %", "---", "Saltar hojas vacías", "---",
                "Más opciones…");
        assertThat(item(menu, "A4 (21 × 29,7 cm)").isSelected()).isTrue();
        assertThat(item(menu, "100 %").isSelected()).isTrue();
        assertThat(view.selection().ids()).isEmpty();
    }

    @Test
    void aCustomPercentageIsOfferedToo() {
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(60)));

        JPopupMenu menu = openPopup();

        assertThat(texts(menu)).contains("60 %");
        assertThat(item(menu, "60 %").isSelected()).isTrue();
    }

    @Test
    void aPickRemembersTheOptionAndRedrawsWithoutTouchingTheCircuit() {
        JPopupMenu menu = openPopup();
        item(menu, "A3 (29,7 × 42 cm)").doClick();
        assertThat(preferences.printOptions("").paper()).isEqualTo(Paper.A3);
        assertThat(summary()).startsWith("A3 vertical");

        openPopup();
        item(shownMenus.getFirst(), "Horizontal").doClick();
        assertThat(preferences.printOptions("").orientation()).isEqualTo(PageOrientation.LANDSCAPE);

        shownMenus.clear();
        item(openPopup(), "75 %").doClick();
        assertThat(preferences.printOptions("").scaleMode()).isEqualTo(new ScaleMode.Percentage(75));
        assertThat(summary()).startsWith("A3 horizontal · ");

        assertThat(openDocument.isModified()).isFalse();
        assertThat(openDocument.history().canUndo()).isTrue(); // only the two parts
        openDocument.history().undo();
        openDocument.history().undo();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void moreOptionsOpensTheExportDialog() {
        int[] opened = {0};
        Runnable remover = view.registerShortcutAction("file.exportPdf", new AbstractAction("Exportar a PDF…") {
            @Override
            public void actionPerformed(ActionEvent event) {
                opened[0]++;
            }
        });
        try {
            item(openPopup(), "Más opciones…").doClick();
        } finally {
            remover.run();
        }

        assertThat(opened[0]).isEqualTo(1);
    }
}
