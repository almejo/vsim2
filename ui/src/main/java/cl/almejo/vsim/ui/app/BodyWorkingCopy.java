package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editable copy of one definition's body that every body place of it on one document shares (spec §10.3: two
 * tabs of the same component are two views of one copy). Built with the body's ids and coordinates; compiled with the
 * document's registry so its net ids are the flattener's for that body. It follows the document: when the definition
 * or one it uses is replaced from elsewhere (an undo, «Actualizar desde la librería», the AI) the copy is brought to
 * the new body in place, ids kept ({@link #synchronize()}); an edit made through a place of it changes the copy first
 * and is never mistaken for one from elsewhere ({@link #writing}). EDT only.
 */
final class BodyWorkingCopy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BodyWorkingCopy.class);

    private final OpenDocument parent;
    private final String definitionId;
    private final CircuitDocument document;
    private final NetlistCompiler compiler;
    /** The definition and those it uses, as the copy last matched them: compared by identity, cheap per change. */
    private List<ComponentDefinition> matched;
    /** Grows each time the copy follows a change made elsewhere; each place compares it with the one it saw. */
    private long refreshes;
    private boolean removed;
    /** True while a place of this copy executes an edit: the document's change is this copy's own. */
    private boolean writing;
    private int users;
    private Netlist netlist = Netlist.EMPTY;
    private long netlistToken = -1;

    BodyWorkingCopy(OpenDocument parent, ComponentDefinition definition) {
        this.parent = parent;
        this.definitionId = definition.id();
        this.document = definition.bodyDocument(parent.document());
        this.compiler = new NetlistCompiler(parent.registry());
        this.matched = tracked(definition);
        document.addListener((changed, change) -> compile());
        compile();
    }

    CircuitDocument document() {
        return document;
    }

    String definitionId() {
        return definitionId;
    }

    /** The working copy's netlist (net ids as the flattener names them inside an instance, without the prefix). */
    Netlist netlist() {
        return netlist;
    }

    /** The working copy's structure revision the netlist was compiled at; compare with its structure revision. */
    long netlistToken() {
        return netlistToken;
    }

    long refreshes() {
        return refreshes;
    }

    boolean isRemoved() {
        return removed;
    }

    /** One more place shows this copy. */
    void acquire() {
        users++;
    }

    /** One place less; true when none is left and the copy can be dropped. */
    boolean release() {
        users--;
        return users <= 0;
    }

    /** Runs {@code edit} (a place executing on the document) as this copy's own change. */
    void write(Runnable edit) {
        boolean wasWriting = writing;
        writing = true;
        try {
            edit.run();
        } finally {
            writing = wasWriting;
        }
        matched = parent.document().definition(definitionId).map(this::tracked).orElse(matched);
    }

    /**
     * Brings the copy in line with the document after any change of it; does nothing when the definition and the
     * ones it uses are the very same objects as last time, and only takes note during this copy's own edit. A
     * definition gone from the document marks the copy {@linkplain #isRemoved() removed}.
     */
    void synchronize() {
        if (removed) {
            return;
        }
        Optional<ComponentDefinition> definition = parent.document().definition(definitionId);
        if (definition.isEmpty()) {
            removed = true;
            return;
        }
        List<ComponentDefinition> current = tracked(definition.get());
        if (sameObjects(current, matched)) {
            return;
        }
        matched = current;
        if (writing) {
            return;
        }
        ReplaceContentCommand refresh = ReplaceContentCommand.between(document,
                definition.get().bodyDocument(parent.document()), "inside.refresh");
        if (!refresh.changesNothing()) {
            refresh.apply(document);
        }
        refreshes++;
    }

    /** The definition and every definition its body uses, at any depth. */
    private List<ComponentDefinition> tracked(ComponentDefinition definition) {
        List<ComponentDefinition> all = new ArrayList<>();
        all.add(definition);
        all.addAll(parent.document().definitionsUsedBy(definition.body().components()));
        return all;
    }

    private static boolean sameObjects(List<ComponentDefinition> first, List<ComponentDefinition> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) {
                return false;
            }
        }
        return true;
    }

    /** Recompiles the copy's netlist; never throws (it runs as a document listener while a command applies). */
    private void compile() {
        try {
            netlist = compiler.compile(document);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not compile the body of {}", definitionId, exception);
            netlist = Netlist.EMPTY;
        }
        netlistToken = document.structureRevision();
    }
}
