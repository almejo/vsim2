package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed operation of an AI edit (spec §10). A call carries a list of them, applied all or nothing in order. A
 * target is a real element id ({@code c12}) or {@code new:<alias>} for something an earlier operation of the same call
 * made under that alias. Positions and deltas are grid units, never pixels.
 */
public sealed interface EditOperation {

    /** The operation's name in messages to the AI: {@code PlacePart}, {@code Connect}, … */
    default String kind() {
        return getClass().getSimpleName();
    }

    /** Places a part of {@code typeId} with its origin at ({@code x}, {@code y}). */
    record PlacePart(Optional<String> alias, String typeId, int x, int y, Rotation rotation,
                     Map<String, Object> parameters, String label) implements EditOperation {
        public PlacePart {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(rotation, "rotation");
            parameters = Map.copyOf(parameters);
            Objects.requireNonNull(label, "label");
        }
    }

    /** Changes some parameters of a part; the ones not named keep their value. */
    record SetParameters(String target, Map<String, Object> parameters) implements EditOperation {
        public SetParameters {
            Objects.requireNonNull(target, "target");
            parameters = Map.copyOf(parameters);
        }
    }

    /** The label of a part, the text of a note or the title of a frame. */
    record SetLabel(String target, String text) implements EditOperation {
        public SetLabel {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * Joins two pins with an orthogonal route through the {@code via} points, if any (A§8). VSim checks what the
     * route joins and refuses one that would join a net it was not asked to join.
     */
    record Connect(PinTarget from, PinTarget to, List<GridPoint> via) implements EditOperation {
        public Connect {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            via = List.copyOf(via);
        }
    }

    /** A wire through {@code points}, each one straight across or down from the one before. */
    record AddWire(List<GridPoint> points) implements EditOperation {
        public AddWire {
            points = List.copyOf(points);
        }
    }

    /** Moves the targets by the delta; the wires attached to them follow, as when the user drags them. */
    record Move(List<String> targets, int deltaX, int deltaY) implements EditOperation {
        public Move {
            targets = List.copyOf(targets);
        }
    }

    /** Deletes the targets (a locked frame with what it holds). */
    record Delete(List<String> targets) implements EditOperation {
        public Delete {
            targets = List.copyOf(targets);
        }
    }

    /** A titled frame around the targets, or with exactly {@code bounds} when given. */
    record MakeFrame(Optional<String> alias, String title, List<String> targets, Optional<Bounds> bounds,
                     boolean locked) implements EditOperation {
        public MakeFrame {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(title, "title");
            targets = List.copyOf(targets);
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    /** A text note, such as the «Prueba: …» notes of the bundled examples. */
    record AddNote(Optional<String> alias, String text, int x, int y) implements EditOperation {
        public AddNote {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * An alignment guide across the canvas. {@code layoutOnly}: the AI adds it only to align while it works, so it
     * goes away when the task ends unless the task's end asks to keep it (spec §7); a guide that was there already is
     * never taken away.
     */
    record AddGuide(Guide.Orientation orientation, int position, boolean layoutOnly) implements EditOperation {
        public AddGuide {
            Objects.requireNonNull(orientation, "orientation");
        }

        /** A guide that stays (not only to align). */
        public AddGuide(Guide.Orientation orientation, int position) {
            this(orientation, position, false);
        }
    }

    /** Takes an alignment guide away. */
    record RemoveGuide(Guide.Orientation orientation, int position) implements EditOperation {
        public RemoveGuide {
            Objects.requireNonNull(orientation, "orientation");
        }
    }

    /**
     * «Crear componente desde selección» on the targets: a user component named {@code name} whose pins are the
     * targets' interface pins and test parts (every test part becomes pins, as the dialog proposes). {@code pinNames}
     * renames pins: key an interface pin, or a test part that gives one pin. With {@code replaceSelection} the targets
     * are replaced by one instance; without it they stay, as «cómo se hizo» (spec §6). {@code alias} names the new
     * definition for {@link PlaceInstance} in the same call.
     */
    record CreateComponent(Optional<String> alias, String name, List<String> targets, Map<String, String> pinNames,
                           boolean replaceSelection) implements EditOperation {
        public CreateComponent {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(name, "name");
            targets = List.copyOf(targets);
            pinNames = Map.copyOf(pinNames);
        }
    }

    /** Places an instance of the circuit's user component {@code definition}: its name, its id or {@code new:<alias>}. */
    record PlaceInstance(Optional<String> alias, String definition, int x, int y, Rotation rotation)
            implements EditOperation {
        public PlaceInstance {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(rotation, "rotation");
        }
    }

    /** «Ordenar cables» on the targets' wires (every wire of the circuit when there are no targets). */
    record TidyWires(List<String> targets) implements EditOperation {
        public TidyWires {
            targets = List.copyOf(targets);
        }
    }
}
