package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/** The JSON shape of a definition, shared by circuit files, clipboard fragments and component files. */
final class DefinitionEntries {

    record PinEntry(String name, int x, int y, String direction, int width, String source) {
    }

    record DefinitionEntry(String id, String name, String description, int width, int height, List<PinEntry> pinout,
                           List<CircuitFormat.ComponentEntry> components, List<CircuitFormat.WireEntry> wires,
                           List<CircuitFormat.LabelEntry> labels,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Long revision,
                           @JsonInclude(JsonInclude.Include.NON_NULL) AppearanceEntries.DrawingEntry appearance) {
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
                definition.revision(), definition.appearance().map(AppearanceEntries::entry).orElse(null));
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
            ComponentDefinition definition = definition(entry);
            if (document.definition(definition.id()).isPresent()) {
                throw invalid("duplicate definition " + definition.id());
            }
            put(document, definition);
            written.add(definition);
        }
        for (ComponentDefinition definition : written) {
            List<Component> normalized = new ArrayList<>();
            for (Component component : definition.body().components()) {
                ComponentType type = registry.find(component.typeId()).orElseThrow(() -> new CircuitFormatException(
                        CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE,
                        component.typeId() + " in definition " + definition.id()));
                try {
                    normalized.add(CircuitFormat.validateAppearance(
                            component.withParameters(type.resolveParameters(component.parameters())), type));
                } catch (IllegalArgumentException | NullPointerException exception) {
                    // A malformed parameter value can surface as a NullPointerException; no unchecked exception may
                    // escape to the caller (on a paste that would reach the EDT), so it becomes INVALID_ELEMENT.
                    throw invalid("definition " + definition.id() + ": " + exception.getMessage(), exception);
                }
            }
            put(document, new ComponentDefinition(definition.id(), definition.name(), definition.description(),
                    definition.size(), definition.pinout(),
                    new CircuitFragment(normalized, definition.body().wires(), definition.body().labels()),
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

    private static ComponentDefinition definition(DefinitionEntry entry) throws CircuitFormatException {
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
            CircuitFragment body = new CircuitFragment(components, wires, labels);
            CircuitDocument duplicateCheck = new CircuitDocument();
            for (DocumentElement element : body.elements()) {
                duplicateCheck.add(element);
            }
            GridSize size = new GridSize(entry.width(), entry.height());
            return new ComponentDefinition(entry.id(), entry.name(), entry.description(), size, pinout, body,
                    Optional.ofNullable(entry.appearance()).map(value -> AppearanceEntries.drawing(value, size)),
                    entry.revision() == null ? 1 : entry.revision());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("definition " + entry.id() + ": " + exception.getMessage(), exception);
        }
    }

}
