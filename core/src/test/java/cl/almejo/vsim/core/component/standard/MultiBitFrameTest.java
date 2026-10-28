package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A multi-bit Switch or LED's bounds are its body plus its lead, so frames see them whole on every side. */
class MultiBitFrameTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void theBodyAndTheLeadAreWithinTheBoundsOnEverySide() {
        for (int width : new int[]{2, 4, 8, 12, 16}) {
            for (String side : MultiBitLayout.SIDES) {
                GridSize size = MultiBitLayout.sizeFor(width, side);
                GridSize box = MultiBitLayout.boxFor(width);
                GridPoint origin = MultiBitLayout.bodyOrigin(width, side);
                GridPoint pin = MultiBitLayout.pinFor(width, side);
                String description = width + " bits, " + side;
                assertThat(origin.x() + box.width()).as(description).isLessThanOrEqualTo(size.width());
                assertThat(origin.y() + box.height()).as(description).isLessThanOrEqualTo(size.height());
                assertThat(pin.x()).as(description).isBetween(0, size.width());
                assertThat(pin.y()).as(description).isBetween(0, size.height());
            }
        }
    }

    @Test
    void aMultiBitSwitchAndLedInsideAFrameAreItsContentsOnEverySide() {
        for (String typeId : new String[]{StandardComponentTypes.SWITCH, StandardComponentTypes.LED}) {
            for (String side : MultiBitLayout.SIDES) {
                CircuitDocument document = new CircuitDocument();
                Map<String, Object> parameters = Map.of("width", 8, MultiBitLayout.SIDE, side);
                ComponentType type = registry.require(typeId).specializedFor(parameters);
                Component part = type.newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R0,
                        parameters, "");
                document.add(part);
                Bounds bounds = type.bounds(part);
                Frame frame = new Frame(document.newFrameId(), new GridPoint(bounds.x() - 2, bounds.y() - 2),
                        bounds.width() + 4, bounds.height() + 4, "Frame", FrameTint.BLUE, true);
                document.add(frame);

                assertThat(FrameContents.of(document, registry, frame)).as(typeId + " " + side)
                        .contains(part.id());
                assertThat(FrameContents.parents(document, registry)).as(typeId + " " + side)
                        .containsEntry(part.id(), frame.id());
            }
        }
    }
}
