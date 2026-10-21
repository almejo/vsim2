package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.snap.PointResolution.Reason;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Wire and frame points (alignment-guides spec §4.4, points rule): connections win, then guides, then grid lines. */
class PointSnapTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final DefaultSnapper snapper = new DefaultSnapper(context.preferences, Dimension::new);
    private GridPoint pin;

    @BeforeEach
    void create() {
        Component gate = place(context.openDocument(), StandardComponentTypes.NOT, 0, 0, Map.of());
        pin = context.openDocument().registry().typeOf(gate).pinLocation(gate, 0);
        context.openDocument().execute(new AddWireCommand(new Wire(context.openDocument().document().newWireId(),
                new GridPoint(40, 0), new GridPoint(50, 0))));
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void guides(Guide... guides) {
        context.openDocument().document().setGuides(List.of(guides));
    }

    private PointResolution resolve(int x, int y) {
        return snapper.snapPoint(context, new GridPoint(x, y));
    }

    @Test
    void aFreePointSnapsToAGuideOneUnitAwayOnEitherSide() {
        guides(new Guide(Orientation.VERTICAL, 31));
        assertThat(resolve(30, 30)).isEqualTo(new PointResolution(new GridPoint(31, 30), Reason.GUIDE,
                List.of(new Guide(Orientation.VERTICAL, 31))));
        guides(new Guide(Orientation.VERTICAL, 29));
        assertThat(resolve(30, 30).point()).isEqualTo(new GridPoint(29, 30));
        guides(new Guide(Orientation.HORIZONTAL, 31));
        assertThat(resolve(30, 30).point()).isEqualTo(new GridPoint(30, 31));
        guides(new Guide(Orientation.HORIZONTAL, 29));
        assertThat(resolve(30, 30).point()).isEqualTo(new GridPoint(30, 29));
    }

    @Test
    void aPointSnapsOnBothAxesAndNamesBothGuides() {
        guides(new Guide(Orientation.VERTICAL, 31), new Guide(Orientation.HORIZONTAL, 29));
        PointResolution resolution = resolve(30, 30);
        assertThat(resolution.point()).isEqualTo(new GridPoint(31, 29));
        assertThat(resolution.reason()).isEqualTo(Reason.GUIDE);
        assertThat(resolution.guides()).containsExactlyInAnyOrder(new Guide(Orientation.VERTICAL, 31),
                new Guide(Orientation.HORIZONTAL, 29));
    }

    @Test
    void atAQuarterTheReachIsSixUnits() {
        context.viewport.zoomAt(0, 0, 0.25);
        guides(new Guide(Orientation.VERTICAL, 36));
        assertThat(resolve(30, 30).point()).isEqualTo(new GridPoint(36, 30));
        guides(new Guide(Orientation.VERTICAL, 37));
        assertThat(resolve(30, 30)).isEqualTo(new PointResolution(new GridPoint(30, 30), Reason.GRID));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 1.0})
    void aPinIsNeverPulledByAGuideOnEitherSide(double zoom) {
        context.viewport.zoomAt(0, 0, zoom);
        guides(new Guide(Orientation.VERTICAL, pin.x() + 1), new Guide(Orientation.HORIZONTAL, pin.y() - 1));
        assertThat(snapper.snapPoint(context, pin)).isEqualTo(new PointResolution(pin, Reason.CONNECTION));
        guides(new Guide(Orientation.VERTICAL, pin.x() - 1), new Guide(Orientation.HORIZONTAL, pin.y() + 1));
        assertThat(snapper.snapPoint(context, pin)).isEqualTo(new PointResolution(pin, Reason.CONNECTION));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 1.0})
    void aWireInteriorOrEndIsNeverPulledByAGuideOrAGridLine(double zoom) {
        context.viewport.zoomAt(0, 0, zoom);
        context.preferences.setSnapToGridLines(true);
        guides(new Guide(Orientation.VERTICAL, 46), new Guide(Orientation.HORIZONTAL, 1));
        assertThat(resolve(45, 0)).isEqualTo(new PointResolution(new GridPoint(45, 0), Reason.CONNECTION));
        guides(new Guide(Orientation.VERTICAL, 44), new Guide(Orientation.HORIZONTAL, -1));
        assertThat(resolve(45, 0).reason()).isEqualTo(Reason.CONNECTION);
        guides(new Guide(Orientation.VERTICAL, 41));
        assertThat(resolve(40, 0)).isEqualTo(new PointResolution(new GridPoint(40, 0), Reason.CONNECTION));
    }

    @Test
    void gridLinesPullOnlyWhenOnAndOnlyOnAnAxisWithoutAGuide() {
        assertThat(resolve(31, 31)).isEqualTo(new PointResolution(new GridPoint(31, 31), Reason.GRID));
        context.preferences.setSnapToGridLines(true);
        PointResolution onLines = resolve(31, 31);
        assertThat(onLines.reason()).isEqualTo(Reason.GRID_LINE);
        assertThat(onLines.point().x() % 2).isZero();
        assertThat(onLines.point().y() % 2).isZero();
        assertThat(onLines.guides()).isEmpty();

        guides(new Guide(Orientation.VERTICAL, 31));
        PointResolution mixed = resolve(30, 31);
        assertThat(mixed.point().x()).isEqualTo(31); // the guide beats the grid line on x
        assertThat(mixed.point().y() % 2).isZero();
        assertThat(mixed.reason()).isEqualTo(Reason.GUIDE);
    }

    @Test
    void withAjustarOffHiddenGuidesOrGuiasOffThePointStays() {
        guides(new Guide(Orientation.VERTICAL, 31));
        context.preferences.setSnapToAlignments(false);
        assertThat(resolve(30, 30)).isEqualTo(new PointResolution(new GridPoint(30, 30), Reason.GRID));
        context.preferences.setSnapToAlignments(true);
        context.preferences.setShowGuides(false);
        assertThat(resolve(30, 30).point()).isEqualTo(new GridPoint(30, 30));
        context.preferences.setShowGuides(true);
        context.preferences.setSnapToGuides(false);
        assertThat(resolve(30, 30).point()).isEqualTo(new GridPoint(30, 30));
    }

    @Test
    void partsNeverPullAPoint() {
        // the gate's right edge is at x = 4; a free point one unit off it stays (no part alignment for points)
        assertThat(resolve(5, 20)).isEqualTo(new PointResolution(new GridPoint(5, 20), Reason.GRID));
    }
}
