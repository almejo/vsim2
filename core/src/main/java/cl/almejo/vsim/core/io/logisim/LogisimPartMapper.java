package cl.almejo.vsim.core.io.logisim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Maps one Logisim component to what VSim makes of it ({@link LogisimMapping}), never failing: a part VSim has no
 * equivalent for is an «unsupported» note, a value it cannot read or VSim refuses an «invalid» one. Every
 * {@link MappedPart} has its parameters resolved by VSim's registry, with Logisim's delay in its «Retardo»
 * ({@link LogisimTiming}). Which VSim part each Logisim part becomes, and how it turns, is the table of
 * {@link LogisimPartRules}.
 */
public final class LogisimPartMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogisimPartMapper.class);

    private final Map<String, LogisimPartRules.Rule> rules;

    /** The mapper of Logisim 2.7's built-in parts. */
    public LogisimPartMapper() {
        this(LogisimPartRules.logisim27());
    }

    /** A mapper with another dialect's table ({@code library/name} → rule). */
    LogisimPartMapper(Map<String, LogisimPartRules.Rule> rules) {
        this.rules = Map.copyOf(rules);
    }

    /** What {@code component} of the context's circuit becomes in VSim. */
    public LogisimMapping map(LogisimComponent component, MappingContext context) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(context, "context");
        PartDraft draft = new PartDraft(component, context);
        if (component.isSubcircuit()) {
            return new MappedSubcircuit(component.name(), component.attributes().textOr("facing", "east"),
                    component.attributes().textOr("label", ""), draft.notes());
        }
        LogisimPartRules.Rule rule = rules.get(LogisimPartRules.key(component.library(), component.name()));
        if (rule == null) {
            return draft.unsupported(LogisimNotes.UNSUPPORTED_PART, component.name());
        }
        try {
            return rule.map(draft);
        } catch (IllegalArgumentException exception) {
            LOGGER.info("Logisim part {} at {} of «{}» not imported: {}", component.name(), component.location(),
                    context.circuit(), exception.getMessage());
            return new PartDraft(component, context).leftOut(ImportNote.Kind.INVALID, LogisimNotes.INVALID_PART,
                    component.name());
        }
    }
}
