package cl.almejo.vsim.ui.appearance;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

/**
 * Where the shape editor opens: where the user left it when that place is still on a screen, else centred on the
 * editor window (a monitor unplugged since, or the first time), never larger than the screen. Pure, so it is tested
 * without a display.
 */
final class DialogPlacement {
    /** The part of the dialog's top band that must be on a screen for the user to grab it (as the main windows). */
    private static final int GRAB_WIDTH = 120;
    private static final int GRAB_HEIGHT = 20;

    private DialogPlacement() { }

    static Rectangle place(Optional<Rectangle> saved, Rectangle owner, List<Rectangle> screens, Dimension defaultSize) {
        if (screens.isEmpty()) {
            return saved.orElseGet(() -> centred(defaultSize, owner));
        }
        if (saved.isPresent() && grabbable(saved.get(), screens)) {
            return saved.get();
        }
        Dimension wanted = saved.map(Rectangle::getSize).orElse(defaultSize);
        Rectangle screen = screenOf(owner, screens);
        Dimension fitted = new Dimension(Math.min(wanted.width, screen.width), Math.min(wanted.height, screen.height));
        Rectangle placed = centred(fitted, owner);
        placed.x = Math.clamp(placed.x, screen.x, screen.x + screen.width - fitted.width);
        placed.y = Math.clamp(placed.y, screen.y, screen.y + screen.height - fitted.height);
        return placed;
    }

    private static boolean grabbable(Rectangle bounds, List<Rectangle> screens) {
        Rectangle topBand = new Rectangle(bounds.x, bounds.y, bounds.width, Math.min(bounds.height, GRAB_HEIGHT));
        int neededWidth = Math.min(bounds.width, GRAB_WIDTH);
        return screens.stream().map(screen -> screen.intersection(topBand))
                .anyMatch(visible -> !visible.isEmpty() && visible.width >= neededWidth
                        && visible.height >= topBand.height);
    }

    private static Rectangle screenOf(Rectangle owner, List<Rectangle> screens) {
        Point centre = new Point((int) owner.getCenterX(), (int) owner.getCenterY());
        return screens.stream().filter(screen -> screen.contains(centre)).findFirst().orElse(screens.getFirst());
    }

    private static Rectangle centred(Dimension size, Rectangle owner) {
        return new Rectangle((int) owner.getCenterX() - size.width / 2, (int) owner.getCenterY() - size.height / 2,
                size.width, size.height);
    }
}
