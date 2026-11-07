package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A connection (a wire drawn, a part placed or moved) and the widths that follow it ({@link ConnectedWidths}), as one
 * undo step named as the connection («Deshacer Cable»): undo puts the old width back and takes the wire away
 * together. The widths are worked out again on every apply, from the document the connection leaves, so redo and a
 * merged run of nudges (one move by the total, see {@link MoveWithWiresCommand}) follow exactly what they connect.
 */
public final class WidthsFollowConnectionCommand implements WrapsCommand {

    /**
     * The netlist the caller already compiled for a document as it is now, if it has one (the editor keeps the
     * netlist of the circuit it shows), so the circuit before the connection is not compiled a second time.
     */
    @FunctionalInterface
    public interface KnownNetlist {

        /** Knows no netlist: the command compiles the circuit before the connection itself. */
        KnownNetlist NONE = document -> Optional.empty();

        /** The netlist of {@code document} exactly as it is now; empty when the caller has none or it is stale. */
        Optional<Netlist> of(CircuitDocument document);
    }

    private final Command connection;
    private final Set<ElementId> placedIds;
    private final ComponentTypeRegistry registry;
    private final KnownNetlist knownNetlist;
    private ConnectedWidths.Followed followed = ConnectedWidths.Followed.NOTHING;

    private WidthsFollowConnectionCommand(Command connection, Set<ElementId> placedIds,
                                          ComponentTypeRegistry registry, KnownNetlist knownNetlist) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.placedIds = Set.copyOf(placedIds);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.knownNetlist = Objects.requireNonNull(knownNetlist, "knownNetlist");
    }

    /** {@code connection} draws wires: the parts it joins that were connected to nothing may follow. */
    public static WidthsFollowConnectionCommand wire(Command connection, ComponentTypeRegistry registry) {
        return wire(connection, registry, KnownNetlist.NONE);
    }

    /** {@link #wire(Command, ComponentTypeRegistry)} reading the circuit before it from {@code knownNetlist}. */
    public static WidthsFollowConnectionCommand wire(Command connection, ComponentTypeRegistry registry,
                                                     KnownNetlist knownNetlist) {
        return new WidthsFollowConnectionCommand(connection, Set.of(), registry, knownNetlist);
    }

    /** {@code connection} places or moves {@code placedIds}: only those parts may follow. */
    public static WidthsFollowConnectionCommand placing(Command connection, Set<ElementId> placedIds,
                                                        ComponentTypeRegistry registry) {
        return placing(connection, placedIds, registry, KnownNetlist.NONE);
    }

    /** {@link #placing(Command, Set, ComponentTypeRegistry)} reading the circuit before it from {@code knownNetlist}. */
    public static WidthsFollowConnectionCommand placing(Command connection, Set<ElementId> placedIds,
                                                        ComponentTypeRegistry registry, KnownNetlist knownNetlist) {
        if (placedIds.isEmpty()) {
            throw new IllegalArgumentException("a placement places something");
        }
        return new WidthsFollowConnectionCommand(connection, placedIds, registry, knownNetlist);
    }

    /** The parts the last apply changed, in the order it changed them; empty before the first apply. */
    public List<WidthChange> widthChanges() {
        return followed.changes();
    }

    @Override
    public Command wrapped() {
        return connection;
    }

    @Override
    public void apply(CircuitDocument document) {
        if (!ConnectedWidths.mayFollow(document, registry, placedIds)) {
            connection.apply(document);
            followed = ConnectedWidths.Followed.NOTHING;
            return;
        }
        document.batch(() -> {
            Netlist before = knownNetlist.of(document)
                    .orElseGet(() -> new NetlistCompiler(registry).compile(document));
            connection.apply(document);
            try {
                followed = ConnectedWidths.follow(document, registry, before, placedIds);
            } catch (RuntimeException exception) {
                connection.revert(document);
                throw exception;
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            followed.revert(document);
            connection.revert(document);
        });
    }

    @Override
    public String description() {
        return connection.description();
    }

    @Override
    public boolean changesNothing() {
        return connection.changesNothing();
    }

    /** Merges as the connection does (a run of nudges): the merged connection follows the widths once, at its end. */
    @Override
    public Optional<Command> mergedWith(Command next) {
        if (!(next instanceof WidthsFollowConnectionCommand following) || following.registry != registry
                || !following.placedIds.equals(placedIds)) {
            return Optional.empty();
        }
        return connection.mergedWith(following.connection)
                .map(merged -> new WidthsFollowConnectionCommand(merged, placedIds, registry, knownNetlist));
    }
}
