package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFonts;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Map;
import java.util.Set;

/**
 * The live preview of the scheme editor (M6.8): a small sample circuit (three switches, a NOT and an AND gate, wires at
 * 0, 1 and Z, an open input, three LEDs, a selected gate, a tinted frame and a free text) painted by the canvas's
 * own {@link CircuitRenderer}, so it uses the same painters and the current text font, over the grid of lines with
 * the dots on top (both grid colours show). The scheme lives in a local
 * {@link Appearance} that only this preview reads.
 */
final class SchemePreview extends JComponent {

    private static final int MARGIN = 2;
    private static final int GAP = 4;

    private final Appearance appearance;
    private final CircuitRenderer renderer;
    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final Netlist netlist;
    private final SimulationSnapshot snapshot;
    private final Set<ElementId> selection;
    private final Bounds sampleBounds;
    private final Runnable repaint = this::repaint;

    SchemePreview(ColorScheme scheme) {
        appearance = new Appearance(scheme);
        renderer = new CircuitRenderer(registry, PainterRegistry.standard(), appearance);
        appearance.addListener(repaint);

        Component firstSwitch = add(StandardComponentTypes.SWITCH, new GridPoint(2, 3), Map.of("value", "0"),
                Messages.text("schemeEditor.sample.firstSwitch"));
        Component not = chained(firstSwitch, StandardComponentTypes.NOT, Map.of(), "");
        chained(not, StandardComponentTypes.LED, Map.of("color", "green"), "");
        Component secondSwitch = add(StandardComponentTypes.SWITCH, new GridPoint(2, 11), Map.of("value", "1"),
                Messages.text("schemeEditor.sample.secondSwitch"));
        Component and = chained(secondSwitch, StandardComponentTypes.AND2, Map.of(), "");
        chained(and, StandardComponentTypes.LED, Map.of(), "");
        Component thirdSwitch = add(StandardComponentTypes.SWITCH, new GridPoint(2, 19), Map.of("value", "Z"),
                Messages.text("schemeEditor.sample.thirdSwitch"));
        chained(thirdSwitch, StandardComponentTypes.LED, Map.of("color", "yellow"), "");
        Bounds circuit = CircuitBounds.of(document, registry).orElseThrow();
        document.add(new TextLabel(document.newLabelId(), new GridPoint(circuit.x(), circuit.maximumY() + 2),
                Messages.text("schemeEditor.sample.text")));
        Bounds content = CircuitBounds.of(document, registry).orElseThrow();
        document.add(new Frame(document.newFrameId(), new GridPoint(content.x() - 2, content.y() - 3),
                Math.max(Frame.MINIMUM_WIDTH, content.width() + 4), Math.max(Frame.MINIMUM_HEIGHT, content.height() + 4),
                Messages.text("schemeEditor.sample.frame"), FrameTint.BLUE, false));
        sampleBounds = CircuitBounds.of(document, registry).orElseThrow();
        selection = Set.of(not.id());

        netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        engine.advance(GAP * 10L);
        snapshot = engine.snapshot();

        setPreferredSize(new Dimension((sampleBounds.width() + 2 * MARGIN) * GridPoint.PIXELS_PER_UNIT,
                (sampleBounds.height() + 2 * MARGIN) * GridPoint.PIXELS_PER_UNIT));
    }

    void setScheme(ColorScheme scheme) {
        appearance.setScheme(scheme);
    }

    ColorScheme scheme() {
        return appearance.scheme();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        TextFonts.addListener(repaint);
    }

    @Override
    public void removeNotify() {
        TextFonts.removeListener(repaint);
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            Viewport viewport = new Viewport();
            viewport.center(sampleBounds, getWidth(), getHeight());
            // Both grids, so the dots' and the lines' colours can be seen while they are edited.
            renderer.paintBackground(canvas, viewport, getWidth(), getHeight(), GridStyle.LINES);
            renderer.paintGridDots(canvas, viewport, getWidth(), getHeight());
            canvas.transform(viewport.documentTransform());
            renderer.paint(canvas, new RenderState(document, netlist, document.revision(), snapshot, selection, 0,
                    RenderState.NO_PROBLEM_GLOW));
        } finally {
            canvas.dispose();
        }
    }

    private Component add(String typeId, GridPoint position, Map<String, Object> parameters, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), position, Rotation.R0,
                parameters, label);
        document.add(component);
        return component;
    }

    /**
     * A component of {@code typeId} whose first input sits {@link #GAP} units right of {@code source}'s output, with
     * the wire between them.
     */
    private Component chained(Component source, String typeId, Map<String, Object> parameters, String label) {
        GridPoint output = registry.typeOf(source).pinLocation(source, firstPin(registry.typeOf(source), false));
        GridPoint input = output.translate(GAP, 0);
        ComponentType type = registry.require(typeId);
        Component atOrigin = type.newComponent(document.newComponentId(), GridPoint.ORIGIN, Rotation.R0, parameters,
                label);
        GridPoint offset = type.pinLocation(atOrigin, firstPin(type, true));
        Component placed = atOrigin.withPosition(new GridPoint(input.x() - offset.x(), input.y() - offset.y()));
        document.add(placed);
        document.add(new Wire(document.newWireId(), output, input));
        return placed;
    }

    private static int firstPin(ComponentType type, boolean input) {
        for (int index = 0; index < type.pinCount(); index++) {
            if (type.pin(index).isInput() == input) {
                return index;
            }
        }
        throw new IllegalArgumentException(type.id() + " has no " + (input ? "input" : "output"));
    }
}
