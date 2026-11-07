package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static org.assertj.core.api.Assertions.assertThat;

/** The picture inside the magnifier (spec §8.2), painted to an image: the body, fitted, with one instance's values. */
class MagnifierPopupTest {

    private static final String BLOCK = "bloque";
    private static final String INSTANCE = "c5";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final OpenDocument openDocument = OpenDocument.createEmpty(registry);
    private final List<OpenDocument> places = new ArrayList<>();

    @AfterEach
    void closeAll() {
        places.forEach(OpenDocument::close);
        openDocument.close();
    }

    @Test
    void theBodysPartsArePaintedOnTheSchemeBackground() {
        withBlock(0, 0, "1");

        BufferedImage image = render(picture(Optional.of(INSTANCE)));

        assertThat(distinctColors(image)).hasSizeGreaterThan(1);
    }

    @Test
    void theHoveredInstancesValuesAreWhatIsPainted() {
        withBlock(0, 0, "1");
        awaitSimulation(openDocument);

        BufferedImage withValues = render(picture(Optional.of(INSTANCE)));
        BufferedImage withoutValues = render(picture(Optional.empty()));

        assertThat(sameImage(withValues, withoutValues)).isFalse();
    }

    @Test
    void aBigBodyIsFittedWithinTheMaximumSize() {
        withBlock(300, 150, "1");

        MagnifierPicture picture = picture(Optional.of(INSTANCE));

        assertThat(picture.getPreferredSize().width).isLessThanOrEqualTo(MagnifierPicture.MAXIMUM_WIDTH);
        assertThat(picture.getPreferredSize().height).isLessThanOrEqualTo(MagnifierPicture.MAXIMUM_HEIGHT);
        assertThat(distinctColors(render(picture))).hasSizeGreaterThan(1);
    }

    @Test
    void aBodyWithNothingInsideStillHasASizeToShow() {
        openDocument.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition(BLOCK, "Bloque", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(), List.of(), List.of())))));
        place();

        MagnifierPicture picture = picture(Optional.of(INSTANCE));

        assertThat(picture.getPreferredSize().width).isPositive();
        assertThat(picture.getPreferredSize().height).isPositive();
    }

    private MagnifierPicture picture(Optional<String> contextPath) {
        OpenDocument place = openDocument.openBody(BLOCK, contextPath);
        places.add(place);
        return new MagnifierPicture(place, PainterRegistry.standard(), () -> ColorScheme.LIGHT);
    }

    private static BufferedImage render(MagnifierPicture picture) {
        picture.setSize(picture.getPreferredSize());
        BufferedImage image = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            picture.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static List<Integer> distinctColors(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()).length == 0
                ? List.of()
                : java.util.Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0,
                image.getWidth())).boxed().distinct().toList();
    }

    private static boolean sameImage(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        return java.util.Arrays.equals(
                first.getRGB(0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth()),
                second.getRGB(0, 0, second.getWidth(), second.getHeight(), null, 0, second.getWidth()));
    }

    /** «bloque»: an input pin and a switch (value {@code value}) at ({@code farX}, {@code farY}), one instance c5. */
    private void withBlock(int farX, int farY, String value) {
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "a");
        Component inner = registry.require(StandardComponentTypes.SWITCH).newComponent(new ElementId("c2"),
                new GridPoint(farX, farY), Rotation.R0, Map.of(StandardComponentTypes.VALUE, value), "");
        openDocument.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition(BLOCK, "Bloque", "",
                new GridSize(4, 4), List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id())),
                new CircuitFragment(List.of(input, inner), List.of(), List.of())))));
        place();
    }

    private void place() {
        openDocument.execute(new AddElementsCommand(List.of(new Component(new ElementId(INSTANCE),
                SubcircuitTypes.typeIdOf(BLOCK), new GridPoint(0, 10), Rotation.R0, Map.of(), ""))));
    }
}
