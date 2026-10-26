package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Hovering a tunnel lights its name, and the context menu travels between the tunnels of a name (spec §7). */
class TunnelFeaturesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;
    private TunnelFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        view.canvas().setSize(800, 600);
        features = new TunnelFeatures(view);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private Component entrance(String name, int x, int y) {
        return place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, x, y, Map.of(Tunnels.NAME, name));
    }

    private Component exit(String name, int x, int y) {
        return place(openDocument, StandardComponentTypes.TUNNEL_EXIT, x, y, Map.of(Tunnels.NAME, name));
    }

    /** The context-menu items the tunnel features add for {@code target}, by text. */
    private List<JMenuItem> menuFor(Component target) {
        JPopupMenu menu = new JPopupMenu();
        features.contributor().contribute(menu, Optional.of(target), view.canvas().selection(),
                new Point2D.Double(target.position().x(), target.position().y()));
        return Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                .toList();
    }

    private static Optional<JMenuItem> item(List<JMenuItem> items, String key) {
        return items.stream().filter(item -> item.getText().equals(Messages.text(key))).findFirst();
    }

    @Test
    void hoveringATunnelLightsEveryTunnelOfItsNameAndCountsThem() {
        Component entrance = entrance("CLK", 0, 0);
        List<Component> exits = List.of(exit("CLK", 20, 0), exit("CLK", 20, 10), exit("CLK", 20, 20));
        exit("SEL", 20, 30);

        features.hoverAt(new Point2D.Double(22, 11));

        assertThat(features.hoveredName()).contains("CLK");
        assertThat(features.highlighted()).containsExactlyInAnyOrder(entrance.id(), exits.get(0).id(),
                exits.get(1).id(), exits.get(2).id());
        assertThat(view.statusBar().text()).contains("CLK: 1 entrada, 3 salidas");
    }

    @Test
    void movingOffTheTunnelsClearsTheHighlightAndTheStatus() {
        exit("CLK", 20, 0);
        features.hoverAt(new Point2D.Double(22, 1));

        features.hoverAt(new Point2D.Double(60, 60));

        assertThat(features.hoveredName()).isEmpty();
        assertThat(features.highlighted()).isEmpty();
        assertThat(view.statusBar().text()).doesNotContain("CLK");
    }

    @Test
    void theHoverFollowsARename() {
        Component entrance = entrance("CLK", 0, 0);
        features.hoverAt(new Point2D.Double(2, 1));
        openDocument.execute(Tunnels.renameCommand(openDocument.document(), openDocument.registry(), entrance.id(),
                "RELOJ"));

        assertThat(features.hoveredName()).contains("RELOJ");
        assertThat(view.statusBar().text()).contains("RELOJ: 1 entrada, 0 salidas");
    }

    @Test
    void goToTheEntradaSelectsItFromASalida() {
        Component entrance = entrance("CLK", 0, 0);
        Component exit = exit("CLK", 40, 30);

        item(menuFor(exit), "tunnel.goToEntrance").orElseThrow().doClick();

        assertThat(view.canvas().selection().ids()).containsExactly(entrance.id());
    }

    @Test
    void aSalidaWithoutEntradaOrAnEntradaOffersNoGoToTheEntrada() {
        Component lonely = exit("LOST", 40, 30);
        Component entrance = entrance("CLK", 0, 0);
        exit("CLK", 40, 0);

        assertThat(item(menuFor(lonely), "tunnel.goToEntrance")).isEmpty();
        assertThat(item(menuFor(entrance), "tunnel.goToEntrance")).isEmpty();
    }

    @Test
    void nextSalidaCyclesThroughTheSalidasOfTheName() {
        Component entrance = entrance("CLK", 0, 0);
        Component first = exit("CLK", 40, 0);
        Component second = exit("CLK", 40, 10);

        item(menuFor(entrance), "tunnel.nextExit").orElseThrow().doClick();
        assertThat(view.canvas().selection().ids()).containsExactly(first.id());
        item(menuFor(first), "tunnel.nextExit").orElseThrow().doClick();
        assertThat(view.canvas().selection().ids()).containsExactly(second.id());
        item(menuFor(second), "tunnel.nextExit").orElseThrow().doClick();
        assertThat(view.canvas().selection().ids()).containsExactly(first.id());
    }

    @Test
    void aLoneSalidaHasNoNextSalida() {
        Component entrance = entrance("CLK", 0, 0);
        Component only = exit("CLK", 40, 0);

        assertThat(item(menuFor(only), "tunnel.nextExit")).isEmpty();
        assertThat(item(menuFor(entrance), "tunnel.nextExit")).isPresent();
    }
}
