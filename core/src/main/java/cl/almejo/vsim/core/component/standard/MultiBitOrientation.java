package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a Switch or LED points, kept in one place only: a one-bit part turns as a whole (the element's rotation, its
 * «side» at the default), a multi-bit part stays upright and moves its pin instead (rotation {@code R0}, the «side»
 * parameter). A part changing form keeps the side its pin is on: a one-bit Switch turned down and widened to 8 bits
 * keeps its pin below; an unturned one-bit LED (its pin below) becomes a multi-bit LED with its pin on the
 * bottom side, and narrowing it back gives the rotation that puts the pin there.
 *
 * <p>A quarter turn of a multi-bit part (see {@link #turnsBySide}) never turns its body: alone it keeps the body where
 * it is and moves the pin to the next side clockwise; in a group its pin goes where the turn takes it, so the wires
 * turned with it stay on it.
 */
public final class MultiBitOrientation {

    private MultiBitOrientation() {
    }

    /** Whether a quarter turn of {@code component} (of {@code type}) moves its pin instead of turning it. */
    public static boolean turnsBySide(Component component, ComponentType type) {
        return MultiBitLayout.defaultSideOf(component.typeId()).isPresent()
                && MultiBitLayout.isMultiBit(type.specializedFor(component.parameters()));
    }

    /**
     * {@code component} in the form its width asks for: a multi-bit Switch or LED upright with its direction in the
     * «side», a one-bit one with its direction in the rotation and the default «side». The pin stays where it was, so
     * the wires on it stay connected (a file whose multi-bit part carries a rotation loads as the circuit it was).
     * Anything already in its form, and any other type, comes back as the same instance.
     */
    public static Component normalized(Component component, ComponentType type) {
        if (MultiBitLayout.defaultSideOf(component.typeId()).isEmpty()) {
            return component;
        }
        return inFormOfWidth(component, type, pinSideOf(component, type));
    }

    /**
     * {@code component} with {@code parameters}. When they move a Switch or LED across one bit (a one-bit Switch
     * turned down, widened to 8 bits) its direction moves between the rotation and the «side», the pin stays on the
     * side it was on and where it was, so the wire on it stays connected; any other change is a plain change of
     * parameters.
     */
    public static Component withParameters(Component component, Map<String, Object> parameters, ComponentType type) {
        Component changed = component.withParameters(parameters);
        if (MultiBitLayout.defaultSideOf(component.typeId()).isEmpty()) {
            return changed;
        }
        boolean changesForm = turnsBySide(component, type) != turnsBySide(changed, type);
        Component normal = changesForm ? inFormOfWidth(changed, type, pinSideOf(component, type))
                : normalized(changed, type);
        return normal == changed ? changed : keepingPin(normal, type, type.pinLocation(component, 0));
    }

    /** The side the pin of a Switch or LED is on, whichever form it is in (counting its rotation). */
    private static String pinSideOf(Component component, ComponentType type) {
        String side = MultiBitLayout.sideOf(type, component.parameters());
        int rotationTurns = component.rotation().quarterTurns();
        if (turnsBySide(component, type)) {
            return turned(side, rotationTurns);
        }
        String defaultSide = MultiBitLayout.defaultSideOf(component.typeId()).orElseThrow();
        String oneBitSide = MultiBitLayout.oneBitSideOf(component.typeId()).orElseThrow();
        return turned(oneBitSide, quarterTurnsBetween(defaultSide, side) + rotationTurns);
    }

    /** {@code component} in the form its width asks for, with its pin on {@code pinSide}. */
    private static Component inFormOfWidth(Component component, ComponentType type, String pinSide) {
        String defaultSide = MultiBitLayout.defaultSideOf(component.typeId()).orElseThrow();
        String oneBitSide = MultiBitLayout.oneBitSideOf(component.typeId()).orElseThrow();
        boolean multiBit = turnsBySide(component, type);
        Rotation rotation = multiBit ? Rotation.R0
                : Rotation.fromQuarterTurns(quarterTurnsBetween(oneBitSide, pinSide));
        String normalSide = multiBit ? pinSide : defaultSide;
        if (rotation == component.rotation() && normalSide.equals(MultiBitLayout.sideOf(type, component.parameters()))) {
            return component;
        }
        Component reoriented = withSide(component, normalSide).withRotation(rotation);
        return keepingPin(reoriented, type, type.pinLocation(component, 0));
    }

    /**
     * A lone multi-bit part after a quarter turn ({@code turn} {@code R90} clockwise, {@code R270} counterclockwise):
     * the pin on the next side, the body exactly where it was, so four turns bring the part back.
     */
    public static Component turnedInPlace(Component component, ComponentType type, Rotation turn) {
        Component upright = normalized(component, type);
        String side = MultiBitLayout.sideOf(type, upright.parameters());
        String next = turned(side, turn.quarterTurns());
        int width = MultiBitLayout.widthOf(type.specializedFor(upright.parameters()));
        GridPoint body = MultiBitLayout.bodyOrigin(width, side);
        GridPoint nextBody = MultiBitLayout.bodyOrigin(width, next);
        return withSide(upright, next).translate(body.x() - nextBody.x(), body.y() - nextBody.y());
    }

    /**
     * A multi-bit part turned with a group about {@code pivot}: its pin goes where the quarter turn {@code turn}
     * takes it and moves to the next side, the body stays upright behind it.
     */
    public static Component turnedAbout(Component component, ComponentType type, GridPoint pivot, Rotation turn) {
        Component upright = normalized(component, type);
        GridPoint pin = type.pinLocation(upright, 0);
        GridPoint turnedPin = pivot.plus(turn.apply(new GridPoint(pin.x() - pivot.x(), pin.y() - pivot.y())));
        String side = MultiBitLayout.sideOf(type, upright.parameters());
        return keepingPin(withSide(upright, turned(side, turn.quarterTurns())), type, turnedPin);
    }

    /** {@code side} after {@code quarterTurns} clockwise quarter turns (negative: counterclockwise). */
    static String turned(String side, int quarterTurns) {
        List<String> sides = MultiBitLayout.SIDES;
        return sides.get(Math.floorMod(sides.indexOf(side) + quarterTurns, sides.size()));
    }

    private static int quarterTurnsBetween(String from, String to) {
        return MultiBitLayout.SIDES.indexOf(to) - MultiBitLayout.SIDES.indexOf(from);
    }

    private static Component withSide(Component component, String side) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(MultiBitLayout.SIDE, side);
        return component.withParameters(parameters);
    }

    /** {@code component} moved so its one pin lands on {@code pin}. */
    private static Component keepingPin(Component component, ComponentType type, GridPoint pin) {
        GridPoint current = type.pinLocation(component, 0);
        return component.translate(pin.x() - current.x(), pin.y() - current.y());
    }
}
