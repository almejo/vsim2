package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static org.assertj.core.api.Assertions.assertThat;

class PrintScopesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);
    private static final double ONE_PIXEL = 1.0 / GridPoint.PIXELS_PER_UNIT;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        openDocument.close();
    }

    private PrintInput capture() {
        return PrintInput.capture(openDocument, Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
    }

    private Frame addFrame(int x, int y, int width, int height, boolean locked) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(x, y), width, height, "Bloque",
                FrameTint.GREEN, locked);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    private static Rectangle2D withMargin(Bounds bounds) {
        int margin = CircuitImage.MARGIN;
        return new Rectangle2D.Double(bounds.x() - margin, bounds.y() - margin, bounds.width() + 2 * margin,
                bounds.height() + 2 * margin);
    }

    @Test
    void anEmptyCircuitHasNothingToExport() {
        PrintInput input = capture();
        assertThat(PrintScopes.area(input, new PrintScope.WholeCircuit())).isEmpty();
        assertThat(PrintScopes.area(input, new PrintScope.Selection(Set.of()))).isEmpty();
        assertThat(PrintScopes.area(input, new PrintScope.Frame(new ElementId("f1")))).isEmpty();
    }

    @Test
    void theWholeCircuitIsItsBoundsPlusTheImageMarginWithoutAClip() {
        Wire wire = addWire(openDocument, 10, 10, 20, 10);
        Component led = UiTestSupport.place(openDocument, StandardComponentTypes.LED, 30, 5, Map.of());
        PrintInput input = capture();
        PrintArea area = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        assertThat(area.gridArea()).isEqualTo(withMargin(
                CircuitBounds.of(input.document(), input.registry()).orElseThrow()));
        assertThat(area.elements()).containsExactly(led.id(), wire.id()); // document order: components first
        assertThat(area.clip()).isEmpty();
    }

    @Test
    void aWholeCircuitWithANegativeOriginKeepsIt() {
        addWire(openDocument, -30, -20, -10, -20);
        PrintArea area = PrintScopes.area(capture(), new PrintScope.WholeCircuit()).orElseThrow();
        assertThat(area.gridArea().getX()).isNegative();
        assertThat(area.gridArea().getY()).isNegative();
    }

    @Test
    void aSelectionHasItsOwnBounds() {
        addWire(openDocument, 0, 0, 5, 0);
        Wire selected = addWire(openDocument, 40, 40, 50, 40);
        PrintInput input = capture();
        PrintArea area = PrintScopes.area(input, new PrintScope.Selection(Set.of(selected.id()))).orElseThrow();
        assertThat(area.elements()).containsExactly(selected.id());
        assertThat(area.gridArea()).isEqualTo(withMargin(DrawnBounds.of(selected, input.registry())));
        assertThat(area.clip()).isEmpty();
    }

    @Test
    void aLockedFrameInTheSelectionExpandsToItsContents() {
        Frame locked = addFrame(0, 0, 30, 20, true);
        Wire inside = addWire(openDocument, 5, 5, 15, 5);
        addWire(openDocument, 50, 50, 60, 50);
        PrintArea area = PrintScopes.area(capture(), new PrintScope.Selection(Set.of(locked.id()))).orElseThrow();
        assertThat(area.elements()).containsExactlyInAnyOrder(locked.id(), inside.id());
    }

    @Test
    void anUnlockedFrameInTheSelectionIsOnlyItsRectangle() {
        Frame unlocked = addFrame(0, 0, 30, 20, false);
        addWire(openDocument, 5, 5, 15, 5);
        PrintArea area = PrintScopes.area(capture(), new PrintScope.Selection(Set.of(unlocked.id()))).orElseThrow();
        assertThat(area.elements()).containsExactly(unlocked.id());
    }

    @Test
    void aSelectionOfElementsThatAreGoneIsEmpty() {
        addWire(openDocument, 0, 0, 5, 0);
        assertThat(PrintScopes.area(capture(), new PrintScope.Selection(Set.of(new ElementId("w99"))))).isEmpty();
    }

    @Test
    void aFrameIsCroppedToItsRectangleWithTheWiresThatCrossItsBorder() {
        Frame frame = addFrame(10, 10, 20, 10, false);
        Wire inside = addWire(openDocument, 12, 15, 20, 15);
        Wire crossing = addWire(openDocument, 20, 12, 50, 12);
        Wire outside = addWire(openDocument, 60, 60, 70, 60);
        PrintArea area = PrintScopes.area(capture(), new PrintScope.Frame(frame.id())).orElseThrow();
        assertThat(area.elements()).contains(frame.id(), inside.id(), crossing.id()).doesNotContain(outside.id());
        Rectangle2D crop = new Rectangle2D.Double(10 - ONE_PIXEL, 10 - ONE_PIXEL, 20 + 2 * ONE_PIXEL,
                10 + 2 * ONE_PIXEL);
        assertThat(area.clip()).contains(crop);
        assertThat(area.gridArea()).isEqualTo(crop);
    }

    @Test
    void aFrameThatIsGoneHasNothingToExport() {
        Frame frame = addFrame(10, 10, 20, 10, false);
        PrintInput input = capture();
        assertThat(PrintScopes.area(input, new PrintScope.Frame(new ElementId("f999")))).isEmpty();
        assertThat(PrintScopes.area(input, new PrintScope.Frame(frame.id()))).isPresent();
    }

    @Test
    void theAreaCannotBeChangedFromOutside() {
        addWire(openDocument, 10, 10, 20, 10);
        PrintArea area = PrintScopes.area(capture(), new PrintScope.WholeCircuit()).orElseThrow();
        area.gridArea().setRect(0, 0, 1, 1);
        assertThat(area.gridArea().getWidth()).isGreaterThan(1);
        assertThat(List.copyOf(area.elements())).hasSize(1);
    }
}
