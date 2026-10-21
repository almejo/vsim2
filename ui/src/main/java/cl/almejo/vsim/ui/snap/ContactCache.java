package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.wiring.ContactProbe;
import cl.almejo.vsim.core.wiring.NewContact;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The contacts of one gesture (spec §3.7), worked out by {@link ContactProbe} once per snapped delta and kept until
 * the document changes (another document, another revision or another registry) or the gesture ends ({@link #clear}).
 * One probe serves every delta of a revision, so the document's pin locations and nets are found once.
 */
public final class ContactCache {

    private record Key(List<DocumentElement> movedElements, int deltaX, int deltaY, Set<ElementId> excludedIds) {
    }

    private final Map<Key, List<NewContact>> contacts = new HashMap<>();
    private CircuitDocument probedDocument;
    private long probedRevision = -1;
    private ComponentTypeRegistry probedRegistry;
    private ContactProbe probe;
    private int probeRuns;

    /**
     * The new contacts of {@code movedElements} translated by the delta, as {@link ContactProbe#newContacts} gives
     * them; {@code registry} resolves both the document's and the moved elements' types.
     */
    public List<NewContact> contacts(CircuitDocument document, ComponentTypeRegistry registry,
                                     List<? extends DocumentElement> movedElements, int deltaX, int deltaY,
                                     Set<ElementId> excludedIds) {
        if (document != probedDocument || document.revision() != probedRevision || registry != probedRegistry) {
            contacts.clear();
            probedDocument = document;
            probedRevision = document.revision();
            probedRegistry = registry;
            probe = null;
        }
        Key key = new Key(List.copyOf(movedElements), deltaX, deltaY, Set.copyOf(excludedIds));
        return contacts.computeIfAbsent(key, missing -> {
            if (probe == null) {
                probe = ContactProbe.of(document, registry);
            }
            probeRuns++;
            return List.copyOf(probe.newContacts(missing.movedElements(), deltaX, deltaY, missing.excludedIds()));
        });
    }

    /** How many document elements the last worked-out delta looked at (those within the moved box). */
    public int lastVisitedCount() {
        return probe == null ? 0 : probe.lastVisitedCount();
    }

    /** For tests: how many times a delta was worked out rather than taken from the cache. */
    int probeRuns() {
        return probeRuns;
    }

    /** Forgets everything (the gesture ended). */
    public void clear() {
        contacts.clear();
        probedDocument = null;
        probedRevision = -1;
        probedRegistry = null;
        probe = null;
    }
}
