package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.LabelLayout;

import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * Texts measured as the canvas draws them ({@link LabelLayout}, with the current label font), for the AI's layout
 * checks. EDT only, like the font.
 */
final class LabelTextMeasure implements TextMeasure {

    static final LabelTextMeasure INSTANCE = new LabelTextMeasure();

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;

    private LabelTextMeasure() {
    }

    @Override
    public Optional<TextBox> componentLabel(ComponentType type, Component component) {
        return LabelLayout.componentLabel(type, component).map(placed -> inGridUnits(placed.bounds()));
    }

    @Override
    public TextBox freeText(TextLabel label) {
        return inGridUnits(LabelLayout.noteBox(label));
    }

    private static TextBox inGridUnits(Rectangle2D pixels) {
        return new TextBox(pixels.getX() / UNIT, pixels.getY() / UNIT, pixels.getWidth() / UNIT,
                pixels.getHeight() / UNIT);
    }
}
