package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.Timer;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** Archivo ▸ «Imprimir…»: where it sits, when it is off and why, and the dialog it opens. */
class PrintFeaturesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:30:00Z"), ZoneOffset.UTC);

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SheetAnchors sheetAnchors = new SheetAnchors();
    private final FakePrinterAccess printers = new FakePrinterAccess();
    private final List<PdfExportDialog> presented = new ArrayList<>();
    private EditorFixture fixture;
    private OpenDocument openDocument;
    private EditorView view;
    private ExportPdfFeatures pdfFeatures;
    private PrintFeatures features;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        openDocument = fixture.openDocument();
        view = fixture.view();
        pdfFeatures = new ExportPdfFeatures(view, collaborators(), (parent, dialog) -> presented.add(dialog), CLOCK,
                sheetAnchors);
    }

    private PdfExportDialog.Collaborators collaborators() {
        return new PdfExportDialog.Collaborators(new PdfExportDialogTest.FakeExporter(),
                (parent, start, name) -> Optional.empty(), (parent, message) -> true, (parent, message) -> { },
                action -> new Timer(Integer.MAX_VALUE, null), Runnable::run, printers);
    }

    private PrintFeatures install() {
        features = new PrintFeatures(view, collaborators(), (parent, dialog) -> presented.add(dialog), CLOCK,
                sheetAnchors);
        return features;
    }

    @AfterEach
    void close() {
        presented.forEach(PdfExportDialog::dispose);
        if (features != null) {
            features.dispose();
        }
        pdfFeatures.dispose();
        fixture.close();
    }

    private void placeSomething() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
    }

    private void openTheFileMenu() {
        JMenu file = view.menu(MenuId.FILE);
        for (MenuListener listener : file.getMenuListeners()) {
            listener.menuSelected(new MenuEvent(file));
        }
    }

    @Test
    void theItemSitsInFileRightAfterSaveAsWithThePrintIcon() {
        install();
        List<String> texts = texts(view.menu(MenuId.FILE));

        // the exports live in Archivo ▸ Exportar; «Imprimir…» stays in Archivo itself
        assertThat(texts.indexOf("Imprimir…")).isEqualTo(texts.indexOf("Guardar como…") + 1);
        assertThat(features.action().getValue(Action.SMALL_ICON)).isSameAs(Icons.get(ExportIcons.PRINT,
                Icons.MENU_SIZE));
        assertThat(view.shortcutActions().get("file.print")).isSameAs(features.action());
    }

    @Test
    void theItemIsOffOnAnEmptyDocument() {
        install();
        assertThat(features.action().isEnabled()).isFalse();
        placeSomething();
        assertThat(features.action().isEnabled()).isTrue();
    }

    @Test
    void withoutPrintersTheItemIsOffAndSaysWhy() {
        printers.printersInstalled(false);
        placeSomething();
        install();

        assertThat(features.action().isEnabled()).isFalse();
        assertThat(features.action().getValue(Action.SHORT_DESCRIPTION)).isEqualTo("No hay impresoras instaladas");
        perform(features.action());
        assertThat(presented).isEmpty();
    }

    @Test
    void untilTheSlowLookupAnswersTheItemStaysOn() {
        printers.slowLookups();
        placeSomething();
        install();
        assertThat(printers.lookups).as("looked up when the view installs").hasSize(1);
        assertThat(features.action().isEnabled()).isTrue();

        printers.lookups.get(0).complete(false);
        assertThat(features.action().isEnabled()).isFalse();
    }

    @Test
    void openingTheFileMenuLooksAgainAndAPrinterAddedTurnsItOn() {
        printers.slowLookups();
        placeSomething();
        install();
        printers.lookups.get(0).complete(false);

        openTheFileMenu();
        assertThat(printers.lookups).hasSize(2);
        printers.lookups.get(1).complete(true);

        assertThat(features.action().isEnabled()).isTrue();
        assertThat(features.action().getValue(Action.SHORT_DESCRIPTION)).isNull();
    }

    @Test
    void aLateAnswerOfAnEarlierLookupChangesNothing() {
        printers.slowLookups();
        placeSomething();
        install();
        openTheFileMenu();

        printers.lookups.get(1).complete(true);
        printers.lookups.get(0).complete(false);

        assertThat(features.action().isEnabled()).isTrue();
    }

    @Test
    void theItemOpensTheDialogToPrint() {
        placeSomething();
        install();

        perform(features.action());

        assertThat(presented).hasSize(1);
        assertThat(presented.get(0).defaultButton()).isSameAs(presented.get(0).printButton());
        assertThat(presented.get(0).title()).isEqualTo("Imprimir");
    }

    @Test
    void theDialogLaysTheWholeCircuitOnTheSheetsAnchoredForThisDocument() {
        preferences.setPrintOptions(new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4,
                PageOrientation.PORTRAIT, new ScaleMode.Percentage(100), true, false, false,
                openDocument.displayName()));
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 1000, 10, Map.of());
        install();
        sheetAnchors.take(openDocument.document(), new cl.almejo.vsim.core.geometry.GridPoint(0, 0));

        perform(features.action());

        assertThat(presented.get(0).sheetNoteText()).isEqualTo("Se usan las hojas que ves en el circuito.");
    }

    @Test
    void disposingTakesBackTheItemTheShortcutAndTheMenuListener() {
        int listeners = view.menu(MenuId.FILE).getMenuListeners().length;
        install();
        features.dispose();

        assertThat(view.shortcutActions()).doesNotContainKey("file.print");
        assertThat(texts(view.menu(MenuId.FILE))).doesNotContain("Imprimir…");
        assertThat(view.menu(MenuId.FILE).getMenuListeners()).hasSize(listeners);
    }
}
