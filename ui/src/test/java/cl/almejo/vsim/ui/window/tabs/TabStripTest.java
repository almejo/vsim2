package cl.almejo.vsim.ui.window.tabs;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TabStripTest {

    private static final Object PROCESSOR = "lrv32im";
    private static final Object COUNTER = "contador";

    private final TabStripModel model = new TabStripModel();
    private TabStrip strip;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model.add(new TabStripModel.Tab("lrv", PROCESSOR, Optional.empty(), "lrv32im", true));
        model.add(new TabStripModel.Tab("ram", PROCESSOR, Optional.of("ram"), "Ram", false));
        model.add(new TabStripModel.Tab("counter", COUNTER, Optional.empty(), "contador", false));
        strip = new TabStrip(model, () -> false);
        strip.setSize(600, TabStrip.HEIGHT);
        strip.doLayout();
    }

    private Rectangle bounds(String tabId) {
        return strip.boundsOf(tabId).orElseThrow();
    }

    private void mouse(int id, int x, int y, int clickCount, int button, int modifiers) {
        strip.dispatchEvent(new MouseEvent(strip, id, 0, modifiers, x, y, x, y, clickCount, false, button));
    }

    private void click(Rectangle bounds, int clickCount, int button) {
        int x = bounds.x + 12;
        int y = bounds.y + bounds.height / 2;
        int modifiers = button == MouseEvent.BUTTON2 ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        mouse(MouseEvent.MOUSE_PRESSED, x, y, clickCount, button, modifiers);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, clickCount, button, 0);
        mouse(MouseEvent.MOUSE_CLICKED, x, y, clickCount, button, 0);
    }

    @Test
    void boundsOfEveryVisibleTabAreInsideTheStrip() {
        Rectangle strip = new Rectangle(0, 0, this.strip.getWidth(), this.strip.getHeight());
        for (TabStripModel.Tab tab : model.visibleTabs()) {
            assertThat(strip.contains(bounds(tab.id()))).as(tab.id()).isTrue();
        }
        assertThat(bounds("lrv").x).isLessThan(bounds("ram").x);
        assertThat(bounds("ram").x).isLessThan(bounds("counter").x);
    }

    @Test
    void aClickOnATabActivatesIt() {
        List<String> activated = new ArrayList<>();
        strip.onActivate(activated::add);
        click(bounds("counter"), 1, MouseEvent.BUTTON1);
        assertThat(activated).containsExactly("counter");
    }

    @Test
    void aMiddleClickClosesTheTab() {
        List<String> closed = new ArrayList<>();
        strip.onClose(closed::add);
        click(bounds("ram"), 1, MouseEvent.BUTTON2);
        assertThat(closed).containsExactly("ram");
    }

    @Test
    void aClickOnTheCrossClosesTheTab() {
        List<String> closed = new ArrayList<>();
        strip.onClose(closed::add);
        Rectangle cross = strip.closeBoundsOf("counter").orElseThrow();
        int x = cross.x + cross.width / 2;
        int y = cross.y + cross.height / 2;
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 1, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 1, MouseEvent.BUTTON1, 0);
        assertThat(closed).containsExactly("counter");
    }

    @Test
    void aDoubleClickOnTheMainTabTogglesFold() {
        List<Object> toggled = new ArrayList<>();
        strip.onFoldToggle(toggled::add);
        click(bounds("lrv"), 2, MouseEvent.BUTTON1);
        assertThat(toggled).containsExactly(PROCESSOR);
        click(bounds("ram"), 2, MouseEvent.BUTTON1);
        assertThat(toggled).as("a component tab does not fold").containsExactly(PROCESSOR);
    }

    @Test
    void aFoldedMainTabShowsItsBadgeAndAClickOnItUnfolds() {
        model.fold(PROCESSOR, true);
        strip.doLayout();
        assertThat(strip.boundsOf("ram")).isEmpty();
        Rectangle badge = strip.badgeBoundsOf("lrv").orElseThrow();
        assertThat(bounds("lrv").contains(badge)).isTrue();
        List<Object> toggled = new ArrayList<>();
        strip.onFoldToggle(toggled::add);
        int x = badge.x + badge.width / 2;
        int y = badge.y + badge.height / 2;
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 1, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 1, MouseEvent.BUTTON1, 0);
        assertThat(toggled).containsExactly(PROCESSOR);
    }

    @Test
    void aDragOfAComponentTabCarriesItsWholeGroup() {
        List<TabStrip.GroupDrop> drops = new ArrayList<>();
        strip.onGroupDropped(drops::add);
        Rectangle ram = bounds("ram");
        int y = ram.y + ram.height / 2;
        int start = ram.x + 10;
        mouse(MouseEvent.MOUSE_PRESSED, start, y, 1, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_DRAGGED, start + 3, y, 0, MouseEvent.NOBUTTON, InputEvent.BUTTON1_DOWN_MASK);
        assertThat(strip.currentDrag()).as("3 px is not a drag yet").isEmpty();
        int end = bounds("counter").x + bounds("counter").width - 2;
        mouse(MouseEvent.MOUSE_DRAGGED, end, y, 0, MouseEvent.NOBUTTON, InputEvent.BUTTON1_DOWN_MASK);
        assertThat(strip.currentDrag()).map(TabTransfer::document).contains(PROCESSOR);
        mouse(MouseEvent.MOUSE_RELEASED, end, y, 1, MouseEvent.BUTTON1, 0);
        assertThat(drops).containsExactly(new TabStrip.GroupDrop(PROCESSOR, new TabStrip.InStrip(1)));
        assertThat(strip.currentDrag()).isEmpty();
    }

    @Test
    void aDragOutOfEveryStripDropsOutside() {
        List<TabStrip.GroupDrop> drops = new ArrayList<>();
        strip.onGroupDropped(drops::add);
        Rectangle counter = bounds("counter");
        int y = counter.y + counter.height / 2;
        mouse(MouseEvent.MOUSE_PRESSED, counter.x + 10, y, 1, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_DRAGGED, counter.x + 10, y + 200, 0, MouseEvent.NOBUTTON,
                InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, counter.x + 10, y + 200, 1, MouseEvent.BUTTON1, 0);
        assertThat(drops).singleElement().satisfies(drop -> {
            assertThat(drop.document()).isEqualTo(COUNTER);
            assertThat(drop.target()).isInstanceOf(TabStrip.Outside.class);
        });
    }

    @Test
    void overflowShowsTheScrollButtonsWhenTabsDoNotFit() {
        assertThat(strip.isOverflowing()).isFalse();
        for (int index = 0; index < 12; index++) {
            model.add(new TabStripModel.Tab("extra" + index, "documento" + index, Optional.empty(),
                    "un circuito con nombre largo " + index, false));
        }
        strip.doLayout();
        assertThat(strip.isOverflowing()).isTrue();
        List<Integer> requested = new ArrayList<>();
        strip.onListRequested(() -> requested.add(1));
        Rectangle list = strip.listButtonBounds().orElseThrow();
        int x = list.x + list.width / 2;
        int y = list.y + list.height / 2;
        mouse(MouseEvent.MOUSE_PRESSED, x, y, 1, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, 1, MouseEvent.BUTTON1, 0);
        assertThat(requested).hasSize(1);
    }

    @Test
    void theActiveTabIsScrolledIntoView() {
        for (int index = 0; index < 12; index++) {
            model.add(new TabStripModel.Tab("extra" + index, "documento" + index, Optional.empty(),
                    "un circuito con nombre largo " + index, false));
        }
        strip.doLayout();
        model.activate("extra11");
        strip.doLayout();
        Rectangle last = bounds("extra11");
        assertThat(last.x + last.width).isLessThanOrEqualTo(strip.tabArea().x + strip.tabArea().width);
        assertThat(last.x).isGreaterThanOrEqualTo(strip.tabArea().x);
    }

    @Test
    void tooltipsNameTheTabAndItsCircuit() {
        Rectangle ram = bounds("ram");
        MouseEvent overRam = new MouseEvent(strip, MouseEvent.MOUSE_MOVED, 0, 0, ram.x + 10, ram.y + 10, 0, false);
        assertThat(strip.getToolTipText(overRam)).isEqualTo("Ram — componente de lrv32im");
        Rectangle lrv = bounds("lrv");
        MouseEvent overLrv = new MouseEvent(strip, MouseEvent.MOUSE_MOVED, 0, 0, lrv.x + 10, lrv.y + 10, 0, false);
        assertThat(strip.getToolTipText(overLrv)).isEqualTo("lrv32im · doble clic: plegar sus componentes");
    }

    @Test
    void foldingWithAnimationShrinksTheComponentTabsThenHidesThem() {
        ManualFrameTicker ticker = new ManualFrameTicker();
        TabStrip animated = new TabStrip(model, () -> true, ticker, ticker::now);
        animated.setSize(600, TabStrip.HEIGHT);
        animated.doLayout();
        int full = animated.boundsOf("ram").orElseThrow().width;
        model.fold(PROCESSOR, true);
        ticker.advance(64);
        animated.doLayout();
        assertThat(animated.boundsOf("ram")).hasValueSatisfying(ram ->
                assertThat(ram.width).isPositive().isLessThan(full));
        ticker.finish();
        animated.doLayout();
        assertThat(animated.boundsOf("ram")).isEmpty();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void withoutAnimationFoldingIsInstant() {
        model.fold(PROCESSOR, true);
        strip.doLayout();
        assertThat(strip.boundsOf("ram")).isEmpty();
    }

    @Test
    void paintsWithoutFailingInBothFoldStates() {
        BufferedImage image = new BufferedImage(600, TabStrip.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        strip.paint(image.createGraphics());
        model.fold(PROCESSOR, true);
        strip.doLayout();
        strip.paint(image.createGraphics());
    }

    private BufferedImage painted() {
        BufferedImage image = new BufferedImage(600, TabStrip.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            strip.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static double distance(int first, int second) {
        java.awt.Color one = new java.awt.Color(first);
        java.awt.Color two = new java.awt.Color(second);
        return Math.abs(one.getRed() - two.getRed()) + Math.abs(one.getGreen() - two.getGreen())
                + Math.abs(one.getBlue() - two.getBlue());
    }

    @Test
    void theGroupBandIsFourPixelsThickAndEveryTabOfTheGroupIsTintedWithItsColour() {
        BufferedImage image = painted();
        Rectangle main = bounds("counter");
        int group = GroupColors.colorOf(COUNTER).getRGB() & 0xFFFFFF;
        int x = main.x + main.width / 2;

        for (int row = 1; row <= 4; row++) {
            assertThat(distance(image.getRGB(x, main.y + row) & 0xFFFFFF, group)).as("band row " + row)
                    .isLessThan(30);
        }
        // below the band, the tab body leans toward the group's colour (an inactive tab too), not the plain strip
        int body = image.getRGB(main.x + 4, main.y + main.height - 4) & 0xFFFFFF;
        int plain = java.awt.Color.WHITE.getRGB() & 0xFFFFFF;
        assertThat(distance(body, group)).isLessThan(distance(plain, group) - 40);
    }
}
