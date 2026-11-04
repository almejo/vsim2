package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * One component's mapping while a rule fills it in: the VSim type, its raw parameters, rotation, label, notes and
 * extras. {@link #part()} has VSim's registry resolve the parameters, so a value VSim refuses fails there (the mapper
 * turns that into an «invalid» note).
 */
final class PartDraft {

    private final LogisimComponent component;
    private final MappingContext context;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final List<ImportNote> notes = new ArrayList<>();
    private String typeId = "";
    private Rotation rotation = Rotation.R0;
    private String label = "";
    private MappedPart.Extras extras = MappedPart.Extras.NONE;

    PartDraft(LogisimComponent component, MappingContext context) {
        this.component = component;
        this.context = context;
    }

    LogisimComponent component() {
        return component;
    }

    MappingContext context() {
        return context;
    }

    LogisimAttributes attributes() {
        return component.attributes();
    }

    String text(String name) {
        return attributes().text(name);
    }

    int integer(String name) {
        return attributes().integer(name);
    }

    boolean bool(String name) {
        return attributes().bool(name);
    }

    PartDraft type(String id) {
        typeId = id;
        return this;
    }

    PartDraft parameter(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    PartDraft delay(int units) {
        return parameter("delay", units);
    }

    /** Turns the part so it faces as the component's {@code facing}; {@code natural} is how VSim's faces at 0. */
    PartDraft facing(LogisimFacing natural) {
        return facing(natural, UnaryOperator.identity());
    }

    /** As {@link #facing(LogisimFacing)}, reading the component's facing through {@code meaning}. */
    PartDraft facing(LogisimFacing natural, UnaryOperator<LogisimFacing> meaning) {
        rotation = meaning.apply(LogisimFacing.parse(text("facing"))).rotationFrom(natural);
        return this;
    }

    /**
     * The part stays at rotation 0 whatever the component's facing: Logisim draws it the same way facing anywhere (a
     * constant's box and value, a clock's square), while VSim would turn its drawing and text; the layout joins its
     * pin to the Logisim end with added wire.
     */
    PartDraft upright() {
        rotation = Rotation.R0;
        return extras(MappedPart.Extras::withUpright);
    }

    /** The label from Logisim's {@code label} attribute. */
    PartDraft labelled() {
        label = attributes().textOr("label", "");
        return this;
    }

    PartDraft extras(UnaryOperator<MappedPart.Extras> change) {
        extras = change.apply(extras);
        return this;
    }

    PartDraft note(ImportNote.Kind kind, String messageKey, String... arguments) {
        notes.add(ImportNote.at(kind, context.circuit(), component.location(), messageKey, arguments));
        return this;
    }

    PartDraft approximated(String messageKey, String... arguments) {
        return note(ImportNote.Kind.APPROXIMATED, messageKey, arguments);
    }

    /**
     * The part, with its parameters resolved by VSim's registry.
     *
     * @throws IllegalArgumentException when VSim refuses a value
     */
    MappedPart part() {
        ComponentType type = context.registry().require(typeId);
        return new MappedPart(typeId, type.resolveParameters(parameters), rotation, label, notes, extras);
    }

    /** Leaves the component out with one note of {@code kind}. */
    NotMapped leftOut(ImportNote.Kind kind, String messageKey, String... arguments) {
        note(kind, messageKey, arguments);
        return new NotMapped(notes);
    }

    /** Leaves the component out: VSim has no equivalent. */
    NotMapped unsupported(String messageKey, String... arguments) {
        return leftOut(ImportNote.Kind.UNSUPPORTED, messageKey, arguments);
    }

    List<ImportNote> notes() {
        return notes;
    }
}
