package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The «Mirar adentro» toggle, its shortcut, the instance menu item and the outline of the hovered instance (spec §8.2). */
class MagnifierFeaturesTest {

    private static final String NAME = "Mirar adentro";
    private static final String MENU_ITEM = "Mirar adentro";
    private static final java.awt.geom.Point2D OUTSIDE = new java.awt.geom.Point2D.Double(5, 5);

    private EditorFixture fixture;
    private EditorView view;
    private MagnifierFeatures features;
    private Component instance;
    private Component gate;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        view = fixture.view();
        fixture.openDocument().execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        instance = new Component(new ElementId("c30"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                new GridPoint(10, 10), Rotation.R0, Map.of(), "");
        gate = fixture.openDocument().registry().require(StandardComponentTypes.BUFFER).newComponent(
                new ElementId("c31"), new GridPoint(40, 10), Rotation.R0, Map.of(), "");
        fixture.openDocument().execute(new AddElementsCommand(List.of(instance, gate)));
        features = new MagnifierFeatures(view, new MagnifierPresenter() {
            @Override
            public void show(cl.almejo.vsim.ui.canvas.CircuitCanvas pane, Component shown, java.awt.geom.Point2D point) {
            }

            @Override
            public void hide() {
            }
        });
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    private List<JCheckBoxMenuItem> viewItems() {
        List<JCheckBoxMenuItem> found = new ArrayList<>();
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && NAME.equals(item.getText())) {
                found.add(item);
            }
        }
        return found;
    }

    private List<AbstractButton> buttons() {
        List<AbstractButton> found = new ArrayList<>();
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && NAME.equals(button.getAction().getValue(Action.NAME))) {
                found.add(button);
            }
        }
        return found;
    }

    private List<String> menuTexts(Component target) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : view.contextMenu(Optional.of(target), new Point2D.Double(0, 0))
                .getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    @Test
    void theToggleIsOffInTheViewMenuAndTheToolbar() {
        assertThat(viewItems()).hasSize(1);
        assertThat(viewItems().get(0).isSelected()).isFalse();
        assertThat(buttons()).hasSize(1);
        assertThat(features.isOn()).isFalse();
    }

    @Test
    void clickingTheButtonTurnsItOnAndTheMenuItemFollows() {
        buttons().get(0).doClick();

        assertThat(features.isOn()).isTrue();
        assertThat(viewItems().get(0).isSelected()).isTrue();
        viewItems().get(0).doClick();
        assertThat(features.isOn()).isFalse();
    }

    @Test
    void theActionIsRegisteredForShortcutsWithoutADefaultKey() {
        assertThat(view.shortcutActions()).containsEntry("view.magnifier", features.toggleAction());
        assertThat(ShortcutCatalog.find("view.magnifier").orElseThrow().defaultStroke()).isEmpty();
    }

    @Test
    void theInstanceMenuOffersMirarAdentroOnlyOnAUserComponent() {
        assertThat(menuTexts(instance)).contains(MENU_ITEM);
        assertThat(menuTexts(gate)).doesNotContain(MENU_ITEM);
    }

    @Test
    void mirarAdentroOpensTheInstanceMenuWithTheEyeIconBeforeASeparator() {
        JPopupMenu menu = view.contextMenu(Optional.of(instance), new Point2D.Double(0, 0));

        assertThat(texts(menu)).startsWith(MENU_ITEM, "---");
        JMenuItem item = (JMenuItem) menu.getComponent(0);
        assertThat(item.getIcon()).as("the eye of the toolbar button").isNotNull();
    }

    @Test
    void withTheToggleOnOnlyTheHoveredInstanceIsOutlined() {
        buttons().get(0).doClick();

        assertThat(markedPixels(instance)).as("no hover, no mark at all").isZero();
        features.hover().pointerMoved(view.canvas(), center(instance));
        assertThat(markedPixels(instance)).isPositive();
        assertThat(markedPixels(gate)).isZero();
        features.hover().pointerExited(OUTSIDE);
        assertThat(markedPixels(instance)).isZero();
    }

    @Test
    void withTheToggleOffNoOutlineIsPainted() {
        features.hover().pointerMoved(view.canvas(), center(instance));

        assertThat(markedPixels(instance)).isZero();
    }

    private java.awt.geom.Point2D center(Component part) {
        Bounds bounds = ElementBounds.of(part, fixture.openDocument().registry());
        return view.canvas().viewport().gridToScreen(bounds.x() + bounds.width() / 2.0,
                bounds.y() + bounds.height() / 2.0);
    }

    @Test
    void disposingTakesBackTheItemTheButtonTheShortcutAndTheMenuContribution() {
        features.dispose();

        assertThat(viewItems()).isEmpty();
        assertThat(buttons()).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("view.magnifier");
        assertThat(menuTexts(instance)).doesNotContain(MENU_ITEM);
    }

    /** Pixels the overlays paint over a blank canvas around {@code part}'s box. */
    private int markedPixels(Component part) {
        BufferedImage image = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            RenderState state = new RenderState(fixture.openDocument().document(), fixture.openDocument().netlist(),
                    fixture.openDocument().netlistToken(), fixture.openDocument().latestSnapshot(), Set.of(), 0,
                    RenderState.NO_PROBLEM_GLOW, Set.of(), PaintPolicy.CANVAS);
            view.canvas().overlays().forEach(overlay -> overlay.paint(graphics, state));
        } finally {
            graphics.dispose();
        }
        Bounds bounds = ElementBounds.of(part, fixture.openDocument().registry());
        int unit = GridPoint.PIXELS_PER_UNIT;
        int count = 0;
        for (int pixelY = (bounds.y() - 4) * unit; pixelY < (bounds.y() + bounds.height() + 4) * unit; pixelY++) {
            for (int pixelX = (bounds.x() - 4) * unit; pixelX < (bounds.x() + bounds.width() + 4) * unit;
                 pixelX++) {
                if (pixelX >= 0 && pixelY >= 0 && pixelX < image.getWidth() && pixelY < image.getHeight()
                        && image.getRGB(pixelX, pixelY) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
