package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A definition edited inside its own tab (spec §6.1, §10.4): the body is the working copy as it is (coordinates and
 * ids untouched), and the pinout follows the body's «Pin de entrada / salida» parts. Where a pin sits on the box comes
 * from the pinout, never from where its part lies inside: a pin whose part still exists keeps its position and
 * direction, takes its name from the part's label and its width from the part; a pin whose part is gone is dropped;
 * a new part gets a pin on a free spot of its side (the box grows when none is free). Name, description, size (unless
 * grown), drawing and id are the definition's; only «Cambiar forma…» and the palette change them.
 */
public final class BodyEdits {

    private BodyEdits() {
    }

    /** One pin of the rebuilt pinout before its name is settled. */
    private record PinDraft(ElementId source, PinDirection direction, GridPoint position, int width,
                            String wantedName, boolean keepsItsName) {
        boolean isInput() {
            return direction == PinDirection.INPUT;
        }
    }

    /** {@code previous} with {@code workingCopy} as its body; {@code previous} itself when nothing changed. */
    public static ComponentDefinition rebuilt(ComponentDefinition previous, CircuitDocument workingCopy) {
        CircuitFragment body = bodyOf(workingCopy);
        Map<ElementId, Component> parts = interfaceParts(workingCopy);
        List<PinDraft> drafts = keptPins(previous, parts);
        Set<ElementId> keptSources = new HashSet<>();
        drafts.forEach(draft -> keptSources.add(draft.source()));
        GridSize size = previous.size();
        Set<GridPoint> occupied = new HashSet<>();
        drafts.forEach(draft -> occupied.add(draft.position()));
        for (Component part : parts.values()) {
            if (keptSources.contains(part.id())) {
                continue;
            }
            boolean input = InterfacePins.INPUT_PIN.equals(part.typeId());
            AppearanceReconciler.EdgeSpot spot = AppearanceReconciler.freeEdgeGrowing(input, size, occupied);
            size = spot.size();
            occupied.add(spot.point());
            drafts.add(new PinDraft(part.id(), input ? PinDirection.INPUT : PinDirection.OUTPUT, spot.point(),
                    InterfacePins.widthOf(part), part.label().strip(), false));
        }
        GridSize finalSize = size;
        Optional<SymbolDrawing> appearance = previous.appearance().map(drawing -> drawing.size().equals(finalSize)
                ? drawing : new SymbolDrawing(finalSize, drawing.elements(), drawing.showPinNames()));
        return new ComponentDefinition(previous.id(), previous.name(), previous.description(), size,
                named(drafts), body, appearance, previous.revision()).revisedFrom(previous);
    }

    /** True when {@code after} has another outside than {@code before}: size, or pins by position, direction, width. */
    public static boolean interfaceChanged(ComponentDefinition before, ComponentDefinition after) {
        return !DefinitionUpdateSafety.sameInterface(before, after);
    }

    /**
     * The working copy as a body: every element at its coordinates. A memory keeps its file link: a link made inside
     * a component is the component's, the same in every piece (the user, 2026-11-06).
     */
    private static CircuitFragment bodyOf(CircuitDocument workingCopy) {
        return new CircuitFragment(workingCopy.components(), workingCopy.wires(), workingCopy.labels(), List.of(),
                workingCopy.frames(), workingCopy.stickyNotes());
    }

    /** The interface-pin parts of the working copy by id, in document order. */
    private static Map<ElementId, Component> interfaceParts(CircuitDocument workingCopy) {
        Map<ElementId, Component> parts = new LinkedHashMap<>();
        workingCopy.components().stream().filter(component -> InterfacePins.isInterface(component.typeId()))
                .forEach(component -> parts.put(component.id(), component));
        return parts;
    }

    /** The previous pins whose part is still there (same id, same kind), in the previous order. */
    private static List<PinDraft> keptPins(ComponentDefinition previous, Map<ElementId, Component> parts) {
        List<PinDraft> drafts = new ArrayList<>();
        for (DefinitionPin pin : previous.pinout()) {
            Component part = parts.get(pin.source());
            String expectedType = pin.spec().direction() == PinDirection.OUTPUT
                    ? InterfacePins.OUTPUT_PIN : InterfacePins.INPUT_PIN;
            if (part == null || !part.typeId().equals(expectedType)) {
                continue;
            }
            String label = part.label().strip();
            String name = label.isEmpty() ? pin.spec().name() : label;
            drafts.add(new PinDraft(pin.source(), pin.spec().direction(), pin.spec().position(),
                    InterfacePins.widthOf(part), name, name.equals(pin.spec().name())));
        }
        return drafts;
    }

    /**
     * The pinout with unique names (ignoring case, as «Crear componente» checks them): pins that keep their name
     * claim it first, a renamed or new pin whose name is taken gets the first free number after it ({@code Y2}), and
     * a new pin without a label is {@code in1}, {@code out1}, ….
     */
    private static List<DefinitionPin> named(List<PinDraft> drafts) {
        Set<String> taken = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<PinDraft, String> names = new LinkedHashMap<>();
        drafts.stream().filter(PinDraft::keepsItsName).forEach(draft -> {
            taken.add(draft.wantedName());
            names.put(draft, draft.wantedName());
        });
        drafts.stream().filter(draft -> !draft.keepsItsName() && !draft.wantedName().isEmpty()).forEach(draft ->
                names.put(draft, taken.add(draft.wantedName()) ? draft.wantedName()
                        : DefinitionBuilder.firstFree(draft.wantedName(), 2, Set.of(), taken)));
        drafts.stream().filter(draft -> draft.wantedName().isEmpty()).forEach(draft -> names.put(draft,
                DefinitionBuilder.firstFree(draft.isInput() ? DefinitionBuilder.INPUT_PREFIX
                        : DefinitionBuilder.OUTPUT_PREFIX, 1, Set.of(), taken)));
        return drafts.stream().map(draft -> new DefinitionPin(new PinSpec(names.get(draft), draft.position(),
                draft.direction(), draft.width()), draft.source())).toList();
    }
}
