package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.logisim.ImportNote;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The notes of an import or an export in the words the report window shows. Grouped by circuit (the whole project
 * first, then the circuits in the order the notes name them); inside a circuit by kind (Diferencias reales, Sin
 * soporte, Aproximado, Inválido, the empty ones left out); inside a kind, a note that says the same thing as another
 * (same message key and same text) is explained once, followed by the list of {@link Place}s it applies to. A place
 * that is a part of the document can be clicked to go there.
 */
record ImportReport(List<CircuitGroup> circuits) {

    /** The kinds in the order they are listed. */
    private static final List<ImportNote.Kind> ORDER = List.of(ImportNote.Kind.DIFFERENT, ImportNote.Kind.UNSUPPORTED,
            ImportNote.Kind.APPROXIMATED, ImportNote.Kind.INVALID);

    /**
     * One circuit's notes; {@code title} says which circuit. {@code place} is the one link of a circuit that is a
     * custom component (all its notes lead to the same instance, so the lines do not repeat it): the instance in the
     * main circuit that holds it, named by its path; empty for the main circuit and the whole project.
     */
    record CircuitGroup(String title, Optional<Place> place, List<KindGroup> kinds) {
        CircuitGroup {
            kinds = List.copyOf(kinds);
        }
    }

    /** One kind's entries inside a circuit; {@code title} says which kind and how many notes. */
    record KindGroup(String title, List<Entry> entries) {
        KindGroup {
            entries = List.copyOf(entries);
        }
    }

    /**
     * One thing to say, once. {@code count} is the number of notes it stands for; {@code places} says where each
     * applies (empty when the notes point nowhere, and then the explanation carries the count).
     */
    record Entry(String explanation, int count, List<Place> places) {
        Entry {
            places = List.copyOf(places);
        }
    }

    /** Where a note applies: {@code element} is the part of the document to go to (empty: plain text). */
    record Place(String text, Optional<ElementId> element) {
    }

    ImportReport {
        circuits = List.copyOf(circuits);
    }

    /**
     * {@code describer} names the part an element id stands for in the document ({@code NOT «U1» (12, 40)}). Every
     * place is a line of its own.
     */
    static ImportReport of(List<ImportNote> notes, Function<ElementId, String> describer) {
        return build(notes, describer, null, null);
    }

    /**
     * The report of a project whose main circuit is {@code mainCircuit}: a circuit of another name is a custom
     * component, and its notes point (through {@code host}) to the one instance that holds it, linked once in the
     * circuit's heading and named by its path ({@link ReportHost#describeInside}); the main circuit's lines keep their
     * own links.
     */
    static ImportReport of(List<ImportNote> notes, ReportHost host, String mainCircuit) {
        return build(notes, host::describe, host, mainCircuit);
    }

    private static ImportReport build(List<ImportNote> allNotes, Function<ElementId, String> describer,
                                      ReportHost host, String mainCircuit) {
        List<ImportNote> notes = allNotes;
        Map<String, List<ImportNote>> byCircuit = new LinkedHashMap<>();
        byCircuit.put("", new ArrayList<>());
        for (ImportNote note : notes) {
            byCircuit.computeIfAbsent(note.circuit(), circuit -> new ArrayList<>()).add(note);
        }
        List<CircuitGroup> circuits = new ArrayList<>();
        byCircuit.forEach((circuit, allCircuitNotes) -> {
            Optional<Place> heading = host == null || circuit.isEmpty() || circuit.equals(mainCircuit)
                    ? Optional.empty() : headingPlace(circuit, allCircuitNotes, host);
            List<ImportNote> circuitNotes = heading.isEmpty() ? allCircuitNotes : allCircuitNotes.stream()
                    .map(note -> note.withElement(Optional.empty())).toList();
            List<KindGroup> kinds = new ArrayList<>();
            for (ImportNote.Kind kind : ORDER) {
                List<ImportNote> ofKind = circuitNotes.stream().filter(note -> note.kind() == kind).toList();
                if (!ofKind.isEmpty()) {
                    kinds.add(new KindGroup(Messages.format(titleKey(kind), ofKind.size()), entries(ofKind, describer)));
                }
            }
            if (!kinds.isEmpty()) {
                circuits.add(new CircuitGroup(circuit.isEmpty() ? Messages.text(LogisimImportTexts.CIRCUIT_PROJECT)
                        : Messages.format(LogisimImportTexts.CIRCUIT_NAMED, circuit), heading, kinds));
            }
        });
        return new ImportReport(circuits);
    }

    /** The instance every note of a custom circuit points to, when they all point to the same one. */
    private static Optional<Place> headingPlace(String circuit, List<ImportNote> notes, ReportHost host) {
        List<ElementId> elements = notes.stream().flatMap(note -> note.element().stream()).distinct().toList();
        if (elements.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(new Place(host.describeInside(elements.getFirst(), circuit), Optional.of(elements.getFirst())));
    }

    boolean isEmpty() {
        return circuits.isEmpty();
    }

    int noteCount() {
        return circuits.stream().flatMap(circuit -> circuit.kinds().stream()).flatMap(kind -> kind.entries().stream())
                .mapToInt(Entry::count).sum();
    }

    private static List<Entry> entries(List<ImportNote> notes, Function<ElementId, String> describer) {
        record Draft(String explanation, List<Place> places, int[] count) {
        }
        Map<String, Draft> drafts = new LinkedHashMap<>();
        for (ImportNote note : notes) {
            String explanation = Messages.format(note.messageKey(), note.arguments().toArray());
            Draft draft = drafts.computeIfAbsent(note.messageKey() + '\u0000' + explanation,
                    key -> new Draft(explanation, new ArrayList<>(), new int[1]));
            draft.count()[0]++;
            place(note, describer).filter(place -> !draft.places().contains(place)).ifPresent(draft.places()::add);
        }
        List<Entry> entries = new ArrayList<>();
        for (Draft draft : drafts.values()) {
            int count = draft.count()[0];
            String explanation = draft.places().isEmpty() && count > 1
                    ? Messages.format(LogisimImportTexts.REPEATED, draft.explanation(), count) : draft.explanation();
            entries.add(new Entry(explanation, count, draft.places()));
        }
        return entries;
    }

    private static Optional<Place> place(ImportNote note, Function<ElementId, String> describer) {
        if (note.element().isPresent()) {
            return Optional.of(new Place(describer.apply(note.element().get()), note.element()));
        }
        return note.where().map(point -> new Place(Messages.format(LogisimImportTexts.PLACE_LOGISIM_POINT,
                String.valueOf(point.x()), String.valueOf(point.y())), Optional.empty()));
    }

    private static String titleKey(ImportNote.Kind kind) {
        return switch (kind) {
            case DIFFERENT -> LogisimImportTexts.KIND_DIFFERENT;
            case APPROXIMATED -> LogisimImportTexts.KIND_APPROXIMATED;
            case UNSUPPORTED -> LogisimImportTexts.KIND_UNSUPPORTED;
            case INVALID -> LogisimImportTexts.KIND_INVALID;
        };
    }
}
