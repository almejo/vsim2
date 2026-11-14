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
import cl.almejo.vsim.ui.window.EditorView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Palette ▸ Memoria ▸ «Máquina de estados» (spec §1 «Create»): the palette entry is not a type to place but a
 * creation, so it is a type of its own that never reaches a circuit; choosing it arms {@link PlaceMachineTool}, and the
 * click calls {@link #create}.
 */
public final class MachineCreation {

    /** The id of the palette entry (favourites, recents and drags would store it; no circuit knows it). */
    public static final String PALETTE_ID = "machine-creation";

    private static final String ID_PREFIX = "maquina-";
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
     * The next machine of {@code document}: id {@code maquina-1}, {@code maquina-2}… and name «Máquina 1»… with the
     * lowest number whose id and name are both free in the document.
     */
    public static ComponentDefinition nextDefinition(CircuitDocument document) {
        Set<String> takenIds = new HashSet<>();
        Set<String> takenNames = new HashSet<>();
        for (ComponentDefinition definition : document.definitions()) {
            takenIds.add(definition.id());
            takenNames.add(definition.name());
        }
        int number = 1;
        while (takenIds.contains(ID_PREFIX + number)
                || takenNames.contains(Messages.format("machine.defaultName", number))) {
            number++;
        }
        return MachineDefinitions.create(ID_PREFIX + number, Messages.format("machine.defaultName", number));
    }

    /**
     * Creates the next machine with its piece's top-left at {@code origin}: definition and piece in one undo step.
     * Returns the executed command ({@link CreateMachineCommand#instanceId()} is the piece).
     */
    public static CreateMachineCommand create(OpenDocument openDocument, GridPoint origin) {
        CreateMachineCommand command = new CreateMachineCommand(nextDefinition(openDocument.document()), origin);
        openDocument.execute(command);
        return command;
    }

    /** Like {@link #create(OpenDocument, GridPoint)} on the view's circuit, selecting the new piece. */
    public static CreateMachineCommand create(EditorView view, GridPoint origin) {
        CreateMachineCommand command = create(view.openDocument(), origin);
        view.selection().select(command.instanceId().orElseThrow());
        return command;
    }
}
