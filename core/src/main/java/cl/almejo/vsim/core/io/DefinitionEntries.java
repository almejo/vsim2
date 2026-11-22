package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPathException;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.MultiBitOrientation;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/**
 * The JSON shape of a definition, shared by circuit files, clipboard fragments and component files. Its body's frames
 * and sticky notes use the circuit's own entries ({@code frames}, {@code stickyNotes}), written only when there are
 * some: a body without them keeps the bytes it had before they were saved.
 */
final class DefinitionEntries {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefinitionEntries.class);

    record PinEntry(String name, int x, int y, String direction, int width, String source) {
    }

    record DefinitionEntry(String id, String name, String description, int width, int height, List<PinEntry> pinout,
                           List<CircuitFormat.ComponentEntry> components, List<CircuitFormat.WireEntry> wires,
                           List<CircuitFormat.LabelEntry> labels,
                           @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CircuitFormat.FrameEntry> frames,
                           @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CircuitFormat.StickyNoteEntry> stickyNotes,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Long revision,
                           @JsonInclude(JsonInclude.Include.NON_NULL) AppearanceEntries.DrawingEntry appearance,
                           @JsonInclude(JsonInclude.Include.NON_NULL) MachineEntries.MachineEntry machine) {
    }

    private DefinitionEntries() {
    }

    static DefinitionEntry entry(ComponentDefinition definition) {
        return new DefinitionEntry(definition.id(), definition.name(), definition.description(),
                definition.size().width(), definition.size().height(),
                definition.pinout().stream().map(pin -> new PinEntry(pin.spec().name(), pin.spec().position().x(),
                        pin.spec().position().y(), pin.spec().direction().name(), pin.spec().width(),
                        pin.source().value())).toList(),
                definition.body().components().stream().map(CircuitFormat::componentEntry).toList(),
                definition.body().wires().stream().map(CircuitFormat::wireEntry).toList(),
                definition.body().labels().stream().map(CircuitFormat::labelEntry).toList(),
                definition.body().frames().stream().map(CircuitFormat::frameEntry).toList(),
                definition.body().stickyNotes().stream().map(CircuitFormat::stickyNoteEntry).toList(),
                definition.revision(), definition.appearance().map(AppearanceEntries::entry).orElse(null),
                definition.machine().map(MachineEntries::entry).orElse(null));
    }

    /**
     * Puts the definitions into {@code document}: first as written, so they may refer to each other in any order, then
     * again with every body component's type checked against {@code registry} (which must resolve the document's
     * definitions) and its parameters normalized.
     */
    static void readInto(CircuitDocument document, List<DefinitionEntry> entries, ComponentTypeRegistry registry)
            throws CircuitFormatException {
        List<ComponentDefinition> written = new ArrayList<>();
        for (DefinitionEntry entry : entries) {
            CircuitFormat.requireEntry(entry, "definitions");
            ComponentDefinition definition = definition(entry, document);
            if (document.definition(definition.id()).isPresent()) {
                throw invalid("duplicate definition " + definition.id());
            }
            put(document, definition);
            written.add(definition);
        }
        for (ComponentDefinition definition : written) {
            if (definition.isMachine()) {
                continue; // its body was generated from the model: standard interface pins with default parameters
            }
            List<Component> normalized = new ArrayList<>();
            for (Component component : definition.body().components()) {
                ComponentType type = registry.find(component.typeId()).orElseThrow(() -> new CircuitFormatException(
                        CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE,
                        component.typeId() + " in definition " + definition.id()));
                try {
                    normalized.add(MultiBitOrientation.normalized(CircuitFormat.validateMemoryContents(
                            CircuitFormat.validateAppearance(component.withParameters(
                                    type.resolveParameters(component.parameters())), type), type), type));
                } catch (IllegalArgumentException | NullPointerException exception) {
                    // A malformed parameter value can surface as a NullPointerException; no unchecked exception may
                    // escape to the caller (on a paste that would reach the EDT), so it becomes INVALID_ELEMENT.
                    throw invalid("definition " + definition.id() + ": " + exception.getMessage(), exception);
                }
            }
            put(document, new ComponentDefinition(definition.id(), definition.name(), definition.description(),
                    definition.size(), definition.pinout(),
                    new CircuitFragment(normalized, definition.body().wires(), definition.body().labels(), List.of(),
                            definition.body().frames(), definition.body().stickyNotes()),
                    definition.appearance(), definition.revision()));
        }
    }

    private static void put(CircuitDocument document, ComponentDefinition definition) throws CircuitFormatException {
        try {
            document.putDefinition(definition);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private static ComponentDefinition definition(DefinitionEntry entry, CircuitDocument document)
            throws CircuitFormatException {
        if (entry.machine() == null) {
            return circuitDefinition(entry, document);
        }
        return machineDefinition(entry, MachineEntries.machine(entry.machine(), entry.id()), document);
    }

    /**
     * A «Máquina de estados»: the written body and pinout are only a copy of what the model generates (spec §2.2),
     * so the model wins when they disagree (a hand-edited file, a body written by another build); a drawn appearance
     * is kept when the written definition is valid.
     */
    private static ComponentDefinition machineDefinition(DefinitionEntry entry, StateMachine machine,
            CircuitDocument document) throws CircuitFormatException {
        ComponentDefinition written;
        try {
            written = circuitDefinition(entry, document);
        } catch (CircuitFormatException exception) {
            LOGGER.warn("The body written for machine {} is not valid; generating it from the machine",
                    entry.id(), exception);
            return generatedMachine(entry, machine);
        }
        try {
            ComponentDefinition regenerated = written.withMachine(machine);
            if (disagrees(written, regenerated)) {
                LOGGER.warn("The body written for machine {} disagrees with the machine; generating it again",
                        entry.id());
            }
            return regenerated;
        } catch (IllegalArgumentException exception) {
            throw invalid("definition " + entry.id() + ": " + exception.getMessage(), exception);
        }
    }

    /** Whether the body and pinout {@code entry} wrote differ from what {@code machine} generates. */
    static boolean writtenBodyDisagrees(DefinitionEntry entry, StateMachine machine) throws CircuitFormatException {
        ComponentDefinition written = circuitDefinition(entry, new CircuitDocument());
        return disagrees(written, written.withMachine(machine));
    }

    /**
     * Whether the written body and pinout differ from the generated ones. A written component has only the parameters
     * the writer keeps (its defaults are left out, and a machine's body is never normalized on reading), so a
     * generated component equals it when it has every written parameter with the same value, whatever defaults it
     * adds; that gap alone is no disagreement (it made every file with a machine log one).
     */
    private static boolean disagrees(ComponentDefinition written, ComponentDefinition regenerated) {
        CircuitFragment body = written.body();
        CircuitFragment generated = regenerated.body();
        if (!regenerated.pinout().equals(written.pinout()) || !regenerated.size().equals(written.size())
                || body.components().size() != generated.components().size()
                || !body.wires().equals(generated.wires()) || !body.labels().equals(generated.labels())
                || !body.frames().equals(generated.frames())
                || !body.stickyNotes().equals(generated.stickyNotes())) {
            return true;
        }
        for (int index = 0; index < body.components().size(); index++) {
            if (!sameComponent(body.components().get(index), generated.components().get(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameComponent(Component written, Component generated) {
        if (!generated.withParameters(written.parameters()).equals(written)) {
            return false;
        }
        for (Map.Entry<String, Object> parameter : written.parameters().entrySet()) {
            Object value = generated.parameters().get(parameter.getKey());
            if (value == null || !String.valueOf(value).equals(String.valueOf(parameter.getValue()))) {
                return false;
            }
        }
        return true;
    }

    private static ComponentDefinition generatedMachine(DefinitionEntry entry, StateMachine machine)
            throws CircuitFormatException {
        try {
            MachineBody generated = MachineBody.of(machine);
            return new ComponentDefinition(entry.id(), entry.name(), entry.description(), generated.size(),
                    generated.pinout(), generated.body(), Optional.empty(),
                    entry.revision() == null ? 1 : entry.revision(), Optional.of(machine));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("definition " + entry.id() + ": " + exception.getMessage(), exception);
        }
    }

    private static ComponentDefinition circuitDefinition(DefinitionEntry entry, CircuitDocument document)
            throws CircuitFormatException {
        try {
            List<DefinitionPin> pinout = new ArrayList<>();
            for (PinEntry pin : CircuitFormat.entries(entry.pinout())) {
                CircuitFormat.requireEntry(pin, "pinout of " + entry.id());
                PinDirection direction = PinDirection.valueOf(String.valueOf(pin.direction()));
                pinout.add(new DefinitionPin(new PinSpec(pin.name(), new GridPoint(pin.x(), pin.y()), direction,
                        pin.width() == 0 ? 1 : pin.width()), new ElementId(String.valueOf(pin.source()))));
            }
            // Parameters stay as written here; readInto normalizes them once every definition's type resolves.
            List<Component> components = new ArrayList<>();
            for (CircuitFormat.ComponentEntry component : CircuitFormat.entries(entry.components())) {
                CircuitFormat.requireEntry(component, "components of " + entry.id());
                CircuitFormat.requireRotation(component);
                components.add(CircuitFormat.component(component, component.parameters()));
            }
            List<Wire> wires = new ArrayList<>();
            for (CircuitFormat.WireEntry wire : CircuitFormat.entries(entry.wires())) {
                wires.add(CircuitFormat.wire(CircuitFormat.requireEntry(wire, "wires of " + entry.id())));
            }
            List<TextLabel> labels = new ArrayList<>();
            for (CircuitFormat.LabelEntry label : CircuitFormat.entries(entry.labels())) {
                labels.add(CircuitFormat.label(CircuitFormat.requireEntry(label, "labels of " + entry.id())));
            }
            List<Frame> frames = new ArrayList<>();
            for (CircuitFormat.FrameEntry frame : CircuitFormat.entries(entry.frames())) {
                frames.add(CircuitFormat.frame(CircuitFormat.requireEntry(frame, "frames of " + entry.id())));
            }
            List<StickyNote> stickyNotes = new ArrayList<>();
            for (CircuitFormat.StickyNoteEntry note : CircuitFormat.entries(entry.stickyNotes())) {
                stickyNotes.add(CircuitFormat.stickyNote(CircuitFormat.requireEntry(note,
                        "stickyNotes of " + entry.id())));
            }
            CircuitFragment body = new CircuitFragment(components, wires, labels, List.of(), frames, stickyNotes);
            CircuitDocument duplicateCheck = new CircuitDocument();
            for (DocumentElement element : body.elements()) {
                duplicateCheck.add(element);
            }
            GridSize size = new GridSize(entry.width(), entry.height());
            return new ComponentDefinition(entry.id(), entry.name(), entry.description(), size, pinout, body,
                    drawing(entry, size, document),
                    entry.revision() == null ? 1 : entry.revision());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("definition " + entry.id() + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * The drawing of a definition. A path VSim cannot read does not fail the load (the user, 2026-11-20): the
     * definition keeps the default box and the document remembers it, so the Problemas list says so. Any other fault
     * of the appearance still fails.
     */
    private static Optional<SymbolDrawing> drawing(DefinitionEntry entry, GridSize size, CircuitDocument document) {
        if (entry.appearance() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(AppearanceEntries.drawing(entry.appearance(), size));
        } catch (SymbolPathException exception) {
            LOGGER.warn("The drawing of definition {} has a path that cannot be read; using the default box: {}",
                    entry.id(), exception.getMessage());
            document.noteUnsupportedDrawing(entry.id());
            return Optional.empty();
        }
    }
}
