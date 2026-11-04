package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The Logisim part one VSim part is becoming, as a rule of {@link LogisimExportRules} builds it: library, name and
 * attributes, the side the VSim part faces at rotation 0 (when the Logisim part has a facing), whether it carries the
 * label, which Logisim end each VSim pin is (by default the end named like the pin, as {@link LogisimEnds} names
 * them) and the notes. A rule that finds no equivalent leaves the part out.
 */
final class ExportDraft {

    private final Component component;
    private final Map<String, Object> parameters;
    private final ExportContext context;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Map<String, Integer> endPins = new HashMap<>();
    /** Each VSim pin's index by its name (the ends may be renamed; the pins are not). */
    private final Map<String, Integer> pinIndexes = new HashMap<>();
    private final List<ImportNote> notes = new ArrayList<>();
    private String library = "";
    private String name = "";
    private Optional<LogisimFacing> naturalFacing = Optional.empty();
    private boolean labelled;
    private boolean leftOut;

    ExportDraft(Component component, ComponentType type, ExportContext context) {
        this.component = component;
        this.parameters = type.resolveParameters(component.parameters());
        this.context = context;
        for (int pin = 0; pin < type.pinCount(); pin++) {
            endPins.put(type.pin(pin).name(), pin);
            pinIndexes.put(type.pin(pin).name(), pin);
        }
    }

    Component component() {
        return component;
    }

    ExportContext context() {
        return context;
    }

    // --- What the rule says ---

    ExportDraft part(String libraryName, String partName) {
        library = libraryName;
        name = partName;
        return this;
    }

    ExportDraft attribute(String attributeName, Object value) {
        attributes.put(attributeName, String.valueOf(value));
        return this;
    }

    /** The side the VSim part faces at rotation 0, as Logisim names it; its rotation turns it. */
    ExportDraft facing(LogisimFacing natural) {
        naturalFacing = Optional.of(natural);
        return this;
    }

    /** The Logisim part shows the VSim label. */
    ExportDraft labelled() {
        labelled = true;
        return this;
    }

    /** Replaces the ends matched by name: from now on only those given with {@link #end} count. */
    ExportDraft endsByPosition() {
        endPins.clear();
        return this;
    }

    /** VSim pin {@code pin} is the Logisim end called {@code endName}. */
    ExportDraft end(String endName, int pin) {
        endPins.put(endName, pin);
        return this;
    }

    /** A note that the part is exported but behaves or looks somewhat differently; the type and label come first. */
    ExportDraft approximated(String messageKey, String... more) {
        notes.add(note(ImportNote.Kind.APPROXIMATED, messageKey, more));
        return this;
    }

    /**
     * A note that the exported part does not do in Logisim what it does in VSim (a «Diferencia real»); the type and
     * label come first.
     */
    ExportDraft different(String messageKey, String... more) {
        notes.add(note(ImportNote.Kind.DIFFERENT, messageKey, more));
        return this;
    }

    /** No Logisim equivalent: the part is left out, noted. */
    void leaveOut(String messageKey, String... more) {
        notes.add(note(ImportNote.Kind.UNSUPPORTED, messageKey, more));
        leftOut = true;
    }

    private ImportNote note(ImportNote.Kind kind, String messageKey, String... more) {
        String[] arguments = Stream.concat(Stream.of(component.typeId(), component.label()), Stream.of(more))
                .toArray(String[]::new);
        return ImportNote.about(kind, context.circuit(), messageKey, arguments)
                .withElement(Optional.of(component.id()));
    }

    // --- The VSim part's parameters ---

    /** A number parameter, also when VSim keeps it as a choice of texts (a multiplexer's {@code inputs}). */
    int integer(String parameter) {
        Object value = parameters.get(parameter);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    /** Whether the VSim pin called {@code pinName} is connected to something; false for a pin the part lacks. */
    boolean isConnected(String pinName) {
        Integer pin = pinIndexes.get(pinName);
        return pin != null && context.connected().test(pin);
    }

    String text(String parameter) {
        return String.valueOf(parameters.get(parameter));
    }

    boolean bool(String parameter) {
        return Boolean.TRUE.equals(parameters.get(parameter));
    }

    // --- The outcome ---

    List<ImportNote> notes() {
        return List.copyOf(notes);
    }

    /** The Logisim part, unless the rule left it out (or never named one). */
    Optional<ExportedPart> result() {
        if (leftOut || name.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> finalAttributes = new LinkedHashMap<>();
        naturalFacing.ifPresent(natural -> finalAttributes.put("facing",
                natural.turnedBy(component.rotation()).text()));
        finalAttributes.putAll(attributes);
        if (labelled && !component.label().isBlank()) {
            finalAttributes.put("label", component.label());
        }
        return Optional.of(new ExportedPart(library, name, finalAttributes, endPins));
    }

    /** What a VSim part becomes in Logisim: the part, its attributes and which VSim pin each named end is. */
    record ExportedPart(String library, String name, Map<String, String> attributes, Map<String, Integer> endPins) {
        ExportedPart {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            endPins = Map.copyOf(endPins);
        }
    }
}
