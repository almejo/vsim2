package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The left column's width (and the circuit tree's height) scale with the interface size: stored «at 100 %», applied
 * × max(size, 100) / 100, at most 30 % of the window when applied; only the user's drag stores.
 */
class LeftColumnWidthTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private JSplitPane split;
    private LeftColumnWidth width;
    private final LeftColumn leftColumn = new LeftColumn(new JLabel("palette"), preferences, appearance::interfaceZoom,
            cl.almejo.vsim.ui.actions.KeyboardFocus.SWING, () -> { });

    @BeforeEach
    void create() {
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JLabel("left"), new JLabel("right"));
        split.setResizeWeight(0);
    }

    @AfterEach
    void close() {
        if (width != null) {
            width.dispose();
        }
    }

    private void open(int windowWidth) {
        width = new LeftColumnWidth(split, preferences, appearance, leftColumn);
        split.setSize(windowWidth, 600);
        split.doLayout();
        flushEvents();
        split.doLayout();
    }

    private static void flushEvents() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException | InvocationTargetException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void zoom(int percent) {
        appearance.setInterfaceZoom(percent);
        split.doLayout();
    }

    private void drag(int pixels) {
        split.setDividerLocation(pixels);
    }

    @Test
    void theWidthIs230At100AndGrowsWithTheSizeUpToThirtyPercentOfTheWindow() {
        open(2000);
        assertThat(split.getDividerLocation()).isEqualTo(230);
        zoom(150);
        assertThat(split.getDividerLocation()).isEqualTo(345);
        zoom(200);
        assertThat(split.getDividerLocation()).isEqualTo(460);
    }

    @Test
    void theCapIsThirtyPercentOfTheWindowWhenApplied() {
        open(1000);
        zoom(200);
        assertThat(split.getDividerLocation()).isEqualTo(300);
        zoom(150);
        assertThat(split.getDividerLocation()).isEqualTo(300);
        zoom(100);
        assertThat(split.getDividerLocation()).isEqualTo(230);
    }

    @Test
    void under100TheWidthStays230() {
        open(2000);
        zoom(75);
        assertThat(split.getDividerLocation()).isEqualTo(230);
    }

    @Test
    void changingTheSizeStoresNothing() {
        open(2000);
        zoom(150);
        zoom(200);
        zoom(75);
        assertThat(preferences.leftColumnWidth()).isEmpty();
    }

    @Test
    void aDragAt150IsStoredAsItsValueAt100AndGrowsAt200() {
        open(2000);
        zoom(150);
        drag(450);
        assertThat(preferences.leftColumnWidth()).hasValue(300);
        zoom(200);
        assertThat(split.getDividerLocation()).isEqualTo(600);
        zoom(100);
        assertThat(split.getDividerLocation()).isEqualTo(300);
    }

    @Test
    void aStoredWidthIsReadAtOpeningWithTheCurrentSize() {
        preferences.setLeftColumnWidth(300);
        appearance.setInterfaceZoom(150);
        open(2000);
        assertThat(split.getDividerLocation()).isEqualTo(450);
    }

    @Test
    void aDragIsNotCappedButTheNextApplicationIs() {
        open(1000);
        drag(500);
        assertThat(split.getDividerLocation()).isEqualTo(500);
        assertThat(preferences.leftColumnWidth()).hasValue(500);
        zoom(125);
        assertThat(split.getDividerLocation()).isEqualTo(300);
        assertThat(preferences.leftColumnWidth()).hasValue(500);
    }

    @Test
    void theCapAlsoAppliesOnTheFirstLayoutOfTheWindow() {
        width = new LeftColumnWidth(split, preferences, appearance, leftColumn);
        appearance.setInterfaceZoom(200);
        assertThat(split.getDividerLocation()).isEqualTo(460); // no window yet: no cap
        split.setSize(1000, 600);
        flushEvents();
        assertThat(split.getDividerLocation()).isEqualTo(300);
    }

    @Test
    void restoringAfterPresentationBringsTheUsersWidthBackAndStoresNothing() {
        preferences.setLeftColumnWidth(260);
        open(2000);
        zoom(150);
        width.apply();
        zoom(100);
        width.apply();
        assertThat(split.getDividerLocation()).isEqualTo(260);
        assertThat(preferences.leftColumnWidth()).hasValue(260);
    }

    @Test
    void scalingFormulas() {
        assertThat(LeftColumnWidth.scaled(230, 75)).isEqualTo(230);
        assertThat(LeftColumnWidth.scaled(230, 100)).isEqualTo(230);
        assertThat(LeftColumnWidth.scaled(170, 150)).isEqualTo(255);
        assertThat(LeftColumnWidth.unscaled(345, 150)).isEqualTo(230);
        assertThat(LeftColumnWidth.unscaled(345, 75)).isEqualTo(345);
    }
}
