package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Palette ▸ Memoria ▸ «Máquina de estados» (spec §1 «Create»): the palette entry is not a type to place but a
 * creation, so it is a type of its own that never reaches a circuit; choosing it arms {@link PlaceMachineTool}, and the
 * click calls {@link #create}.
 * <p>
 * A new machine's id is unique beyond its circuit (review I1, I2): {@code maquina-} and a random suffix that neither
 * the circuit nor «Mis componentes» uses. A paste and the library match user components by id, so a generic
 * {@code maquina-1} would make the first machine of every circuit «the same component» as every other one: pasted
 * into another circuit it became that circuit's machine, and one saved to the library made every new machine look
 * outdated. Its name stays «Máquina N», the next number free in the circuit.
 */
public final class MachineCreation {

    /** The id of the palette entry (favourites, recents and drags would store it; no circuit knows it). */
    public static final String PALETTE_ID = "machine-creation";

    private static final String ID_PREFIX = "maquina-";
    /** Base 36 (digits and lowercase letters), as in a file's ids. */
    private static final int SUFFIX_RADIX = 36;
    /** Six characters: about two thousand million suffixes, so two machines made anywhere practically never meet. */
    private static final int SUFFIX_LENGTH = 6;
    private static final long SUFFIX_COUNT = (long) Math.pow(SUFFIX_RADIX, SUFFIX_LENGTH);
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();
    private static final ComponentType PALETTE_TYPE = new ComponentType(PALETTE_ID, ComponentCategory.MEMORY,
            new GridSize(8, 6), List.of(), List.of(), parameters -> (context, pinIndex) -> {
            });

    private MachineCreation() {
    }

    /** The type the palette lists; it has no pins and no behaviour, only a name, an icon and a place in Memoria. */
    public static ComponentType paletteType() {
        return PALETTE_TYPE;
    }

    /**
     * The next machine of {@code circuit} (the whole circuit, whose definitions include those of every component
     * inside it): a random id not in {@code circuit} nor in {@code libraryIds}, and the name «Máquina N» with the
     * lowest {@code N} no definition of the circuit is called.
     */
    static ComponentDefinition nextDefinition(CircuitDocument circuit, Set<String> libraryIds,
                                              RandomGenerator random) {
        Set<String> takenIds = new HashSet<>(libraryIds);
        Set<String> takenNames = new HashSet<>();
        for (ComponentDefinition definition : circuit.definitions()) {
            takenIds.add(definition.id());
            takenNames.add(definition.name());
        }
        int number = 1;
        while (takenNames.contains(Messages.format("machine.defaultName", number))) {
            number++;
        }
        return MachineDefinitions.create(freeId(takenIds, random), Messages.format("machine.defaultName", number));
    }

    /** A new machine id that none of {@code takenIds} is (a duplicate gets one too, {@link MachineDuplication}). */
    static String freeId(Set<String> takenIds) {
        return freeId(takenIds, RANDOM);
    }

    private static String freeId(Set<String> takenIds, RandomGenerator random) {
        String id;
        do {
            String suffix = Long.toString(Math.floorMod(random.nextLong(), SUFFIX_COUNT), SUFFIX_RADIX);
            id = ID_PREFIX + "0".repeat(SUFFIX_LENGTH - suffix.length()) + suffix;
        } while (takenIds.contains(id));
        return id;
    }

    /**
     * Creates the next machine with its piece's top-left at {@code origin}, in {@code openDocument} (a circuit or the
     * place of a component inside it): definition and piece in one undo step. Its id is also kept clear of
     * {@code libraryIds}, the ids of «Mis componentes». Returns the executed command
     * ({@link CreateMachineCommand#instanceId()} is the piece).
     */
    public static CreateMachineCommand create(OpenDocument openDocument, GridPoint origin, Set<String> libraryIds) {
        Objects.requireNonNull(libraryIds, "libraryIds");
        CircuitDocument circuit = openDocument.parent().orElse(openDocument).document();
        CreateMachineCommand command = new CreateMachineCommand(nextDefinition(circuit, libraryIds, RANDOM), origin);
        openDocument.execute(command);
        return command;
    }
}
