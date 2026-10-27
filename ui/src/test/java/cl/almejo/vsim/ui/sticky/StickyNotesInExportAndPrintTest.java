package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ExportChoice;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.PrintOptions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Sticky notes are reminders: out of the exported image and the printed sheet unless the user asks. */
class StickyNotesInExportAndPrintTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private StickyNote farNote;

    private void circuitWithAFarAwayNote() {
        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(10, 0)));
        farNote = new StickyNote(document.newStickyNoteId(), new GridPoint(200, 150), 8, 6, StickyColor.YELLOW,
                new StickyText("lejos"), false);
        document.add(farNote);
    }

    private Optional<CircuitImage> image(boolean includeStickyNotes) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        return CircuitImage.of(document, netlist, document.revision(), snapshot, registry, PainterRegistry.standard(),
                ColorScheme.LIGHT, Optional.empty(), includeStickyNotes);
    }

    @Test
    void theDefaultImageIgnoresAFarAwayNote() {
        circuitWithAFarAwayNote();
        Bounds withoutTheNote = image(false).orElseThrow().gridBounds();
        assertThat(withoutTheNote.width()).isLessThan(40);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        assertThat(CircuitImage.of(document, netlist, document.revision(), snapshot, registry,
                PainterRegistry.standard(), ColorScheme.LIGHT, Optional.empty()).orElseThrow().gridBounds())
                .isEqualTo(withoutTheNote);
    }

    @Test
    void withTheOptionTheNoteGrowsTheImageAndIsPainted() {
        circuitWithAFarAwayNote();
        CircuitImage included = image(true).orElseThrow();
        assertThat(included.gridBounds().width()).isGreaterThan(200);
        BufferedImage picture = new BufferedImage(included.width(0.25), included.height(0.25),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = picture.createGraphics();
        included.paint(graphics, 0.25);
        graphics.dispose();
        int x = (int) ((farNote.position().x() + 1 - included.gridBounds().x()) * 8 * 0.25);
        int y = (int) ((farNote.position().y() + 3 - included.gridBounds().y()) * 8 * 0.25);
        assertThat(new Color(picture.getRGB(x, y), true)).isEqualTo(ColorScheme.LIGHT.stickyPaper(StickyColor.YELLOW));
    }

    @Test
    void aSelectionThatIsOnlyANoteHasNothingToExportByDefault() {
        circuitWithAFarAwayNote();
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        assertThat(CircuitImage.of(document, netlist, document.revision(), snapshot, registry,
                PainterRegistry.standard(), ColorScheme.LIGHT, Optional.of(Set.of(farNote.id()))))
                .isEmpty();
    }

    @Test
    void theExportChoiceCarriesTheFlagAndDefaultsToOff() {
        ExportChoice plain = new ExportChoice(Path.of("a.png"), ExportChoice.Format.PNG, 1, true);
        assertThat(plain.includeStickyNotes()).isFalse();
        assertThat(ExportChoice.of(Path.of("a"), ExportChoice.Format.PNG, 1, true, true).includeStickyNotes())
                .isTrue();
    }

    @Test
    void theExportPreferenceIsRememberedAndOffByDefault() {
        AppPreferences preferences = AppPreferences.inMemory();
        assertThat(preferences.exportIncludeStickyNotes()).isFalse();
        preferences.setExportIncludeStickyNotes(true);
        assertThat(preferences.exportIncludeStickyNotes()).isTrue();
    }

    @Test
    void thePrintOptionDefaultsToOffAndIsRemembered() {
        AppPreferences preferences = AppPreferences.inMemory();
        assertThat(PrintOptions.defaults("t").includeStickyNotes()).isFalse();
        assertThat(preferences.printIncludeStickyNotes()).isFalse();
        assertThat(preferences.printOptions("t").includeStickyNotes()).isFalse();
        PrintOptions chosen = preferences.printOptions("t");
        preferences.setPrintOptions(new PrintOptions(chosen.scope(), chosen.paper(), chosen.orientation(),
                chosen.scaleMode(), chosen.withValues(), chosen.blackAndWhite(), chosen.headerFooter(), "t", true));
        assertThat(preferences.printIncludeStickyNotes()).isTrue();
        assertThat(preferences.printOptions("t").includeStickyNotes()).isTrue();
    }
}
