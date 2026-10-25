package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.ChangeAppearanceCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.appearance.AppearanceExtension;
import cl.almejo.vsim.ui.appearance.OwnShapeMark;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The dot on a piece with its own shape (spec §6.2): on hover and selection, and «Forma propia» in its help. */
class OwnShapeMarkTest {
    private static final int DOT_X = 84; // top-left corner of the 4 x 4 box at (10, 10)
    private static final int BADGE_X = 108; // its top-right corner, where the library-version badge sits
    private static final int DOT_Y = 84;
    @TempDir Path directory;

    @Test
    void aSelectedPieceWithItsOwnShapeShowsTheDotAndAnUnselectedOneDoesNot() {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            Component shaped = shapedInverter(fixture, 10, 10);
            Runnable remove = install(fixture);
            try {
                OwnShapeMark mark = mark(fixture);
                assertThat(paint(fixture, mark, Set.of())).isZero();
                assertThat(paint(fixture, mark, Set.of(shaped.id()))).isNotZero();
            } finally { remove.run(); }
        }
    }

    @Test
    void theDotIsOnTheOppositeCornerFromTheLibraryVersionBadgeSoAPieceWithBothShowsBoth() throws Exception {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            Component shaped = shapedInverter(fixture, 10, 10);
            Runnable remove = install(fixture);
            try {
                fixture.service.save(new DefinitionBundle(ExampleDefinitions.inverter().withName("Negador"), List.of()));
                LibraryVersionBadge badge = fixture.features.badge();
                assertThat(badge.outdatedIds()).containsExactly(ExampleDefinitions.INVERTER);
                BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                RenderState state = new RenderState(fixture.openDocument.document(), Netlist.EMPTY, 0,
                        SimulationSnapshot.EMPTY, Set.of(shaped.id()), 0);
                badge.paint(graphics, state);
                mark(fixture).paint(graphics, state);
                graphics.dispose();
                assertThat(image.getRGB(DOT_X, DOT_Y) >>> 24).as("own shape dot, top-left").isNotZero();
                assertThat(image.getRGB(BADGE_X, DOT_Y) >>> 24).as("version badge, top-right").isNotZero();
            } finally { remove.run(); }
        }
    }

    @Test
    void hoveringThePieceShowsTheDotAndLeavingItHidesTheDot() {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            shapedInverter(fixture, 10, 10);
            Runnable remove = install(fixture);
            try {
                OwnShapeMark mark = mark(fixture);
                mark.hoverAt(new Point2D.Double(12, 12));
                assertThat(paint(fixture, mark, Set.of())).isNotZero();
                mark.hoverAt(new Point2D.Double(40, 40));
                assertThat(paint(fixture, mark, Set.of())).isZero();
            } finally { remove.run(); }
        }
    }

    @Test
    void aPieceWithoutItsOwnShapeNeverShowsTheDotNorTheHelp() {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
            Component plain = fixture.place("subcircuit:inverter", 10, 10, "");
            Runnable remove = install(fixture);
            try {
                OwnShapeMark mark = mark(fixture);
                mark.hoverAt(new Point2D.Double(12, 12));
                assertThat(paint(fixture, mark, Set.of(plain.id()))).isZero();
                assertThat(mark.tooltipAt(new Point2D.Double(12, 12))).isEmpty();
            } finally { remove.run(); }
        }
    }

    @Test
    void thePieceSaysFormaPropiaButItsPinsKeepTheirOwnHelp() {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            shapedInverter(fixture, 10, 10);
            Runnable remove = install(fixture);
            try {
                OwnShapeMark mark = mark(fixture);
                assertThat(mark.tooltipAt(new Point2D.Double(12, 12))).contains(Messages.text("shape.own"));
                assertThat(mark.tooltipAt(new Point2D.Double(10, 12))).isEmpty(); // on the input pin
                assertThat(mark.tooltipAt(new Point2D.Double(30, 30))).isEmpty(); // outside every piece
            } finally { remove.run(); }
        }
    }

    @Test
    void disposingTheExtensionRemovesTheOverlayAndTheHelp() {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            int overlays = fixture.view.canvas().overlayCount();
            int helpers = fixture.view.canvas().paneHooks().toolTipOverrides();
            int pointerListeners = fixture.view.canvas().getMouseMotionListeners().length;
            Runnable remove = install(fixture);
            assertThat(fixture.view.canvas().overlayCount()).isEqualTo(overlays + 1);
            remove.run();
            assertThat(fixture.view.canvas().overlayCount()).isEqualTo(overlays);
            assertThat(fixture.view.canvas().paneHooks().toolTipOverrides()).isEqualTo(helpers);
            assertThat(fixture.view.canvas().getMouseMotionListeners()).hasSize(pointerListeners);
        }
    }

    private static Component shapedInverter(TemplateFixture fixture, int x, int y) {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(inverter)));
        Component piece = fixture.place("subcircuit:inverter", x, y, "");
        ComponentAppearance appearance = SymbolDraft.of(ComponentAppearance.of(inverter), inverter.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.ALU).appearance();
        fixture.openDocument.execute(ChangeAppearanceCommand.forInstance(fixture.openDocument.document(),
                fixture.openDocument.registry(), piece.id(), Optional.of(appearance)));
        return fixture.openDocument.document().component(piece.id()).orElseThrow();
    }

    private static Runnable install(TemplateFixture fixture) {
        return new AppearanceExtension(fixture.service, fixture.dialogs).install(fixture.view);
    }

    private static OwnShapeMark mark(TemplateFixture fixture) {
        return fixture.view.canvas().overlays().stream().filter(OwnShapeMark.class::isInstance)
                .map(OwnShapeMark.class::cast).findFirst().orElseThrow();
    }

    /** The alpha at the dot's place: the dot is the only thing the overlay paints. */
    private static int paint(TemplateFixture fixture, CanvasOverlay overlay, Set<ElementId> selection) {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(fixture.openDocument.document(), Netlist.EMPTY, 0,
                SimulationSnapshot.EMPTY, selection, 0));
        graphics.dispose();
        return image.getRGB(DOT_X, DOT_Y) >>> 24;
    }
}
