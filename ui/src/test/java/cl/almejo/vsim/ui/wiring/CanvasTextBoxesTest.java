package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;

import static org.assertj.core.api.Assertions.assertThat;

class CanvasTextBoxesTest {

    @Test
    void canvasTextBoxesRoundOutward() {
        // 8 px per unit: 13 px wide starting at 0 covers 2 units
        assertThat(CanvasTextBoxes.outward(new Rectangle2D.Double(0, 0, 13, 8))).isEqualTo(new Bounds(0, 0, 2, 1));
        // starting inside a unit: floor the start, ceil the end (4..17 px is units 0..3)
        assertThat(CanvasTextBoxes.outward(new Rectangle2D.Double(4, -4, 13, 8))).isEqualTo(new Bounds(0, -1, 3, 2));
    }

    @Test
    void aFreeTextBoxStartsAtItsPositionAndHasSomeWidth() {
        TextLabel label = new TextLabel(new ElementId("t1"), new GridPoint(5, 6), "Hola", NoteStyle.PLAIN);
        Bounds bounds = new CanvasTextBoxes(null).freeText(label);
        assertThat(bounds.x()).isEqualTo(5);
        assertThat(bounds.y()).isEqualTo(6);
        assertThat(bounds.width()).isGreaterThanOrEqualTo(1);
    }
}
