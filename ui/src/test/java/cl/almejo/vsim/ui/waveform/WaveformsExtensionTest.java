package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class WaveformsExtensionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final TextPrompt NO_ANSWER = (parent, title, message, initialText) -> Optional.empty();

    private final List<String> messages = new ArrayList<>();
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private OpenDocument openDocument;
    private EditorServices services;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = new EditorServices(PainterRegistry.standard(), appearance, AppPreferences.inMemory(),
                new ComponentLibrary(), List.of(new WaveformsExtension(NO_ANSWER,
                (parent, message) -> messages.add(message))));
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private static JCheckBoxMenuItem toggleItem(EditorView view) {
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && item.getText().equals("Formas de onda")) {
                return item;
            }
        }
        throw new AssertionError("no Formas de onda item");
    }

    private static void perform(JMenuItem item) {
        item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no item " + text);
    }

    private static WaveformPanel panelOf(EditorView view) {
        return (WaveformPanel) view.bottomDock().content().orElseThrow();
    }

    @Test
    void theViewMenuTogglesThePanelUnderTheCanvas() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.VIEW))).endsWith("---", "Formas de onda");
            assertThat(view.bottomDock().content()).containsInstanceOf(WaveformPanel.class);
            assertThat(view.bottomDock().isDockShowing()).isFalse();
            assertThat(toggleItem(view).isSelected()).isFalse();

            perform(toggleItem(view));
            assertThat(view.bottomDock().isDockShowing()).isTrue();
            assertThat(toggleItem(view).isSelected()).isTrue();

            panelOf(view).button("Ocultar").doClick(0);
            assertThat(view.bottomDock().isDockShowing()).isFalse();
            assertThat(toggleItem(view).isSelected()).isFalse();
        } finally {
            view.dispose();
        }
    }

    @Test
    void addingChannelsOpensThePanelAndAnAlreadyShownNetBlinksItsRow() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            perform(item(view.contextMenu(Optional.of(toggle), new Point2D.Double(1, 1)),
                    "Ver pines en formas de onda"));
            assertThat(view.bottomDock().isDockShowing()).isTrue();
            assertThat(panelOf(view).channelNames()).containsExactly("c1.out");

            view.bottomDock().hideDock();
            perform(item(view.contextMenu(Optional.of(toggle), new Point2D.Double(1, 1)),
                    "Ver pines en formas de onda"));
            assertThat(view.bottomDock().isDockShowing()).isTrue();
            assertThat(panelOf(view).isBlinkLit(openDocument.document().probes().get(0).id())).isTrue();
            assertThat(openDocument.document().probes()).hasSize(1);
        } finally {
            view.dispose();
        }
    }

    @Test
    void theSixtyFifthChannelShowsAMessage() {
        List<Component> switches = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            switches.add(place(openDocument, StandardComponentTypes.SWITCH, index * 10, 0, Map.of()));
        }
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            view.canvas().selection().selectAll(openDocument.document());
            perform(item(view.contextMenu(Optional.of(switches.get(0)), new Point2D.Double(1, 1)),
                    "Ver en formas de onda"));
            assertThat(openDocument.document().probes()).hasSize(ProbeActions.MAXIMUM_CHANNELS);
            assertThat(messages).containsExactly("Se alcanzó el máximo de 64 canales");
        } finally {
            view.dispose();
        }
    }

    @Test
    void openingACircuitWithChannelsOpensThePanel() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        openDocument.execute(new SetProbesCommand(List.of(), List.of(new Probe("p1",
                new ProbeTarget.PinTarget(toggle.id(), 0), "reloj")), "command.addProbes"));
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(view.bottomDock().isDockShowing()).isTrue();
            assertThat(panelOf(view).channelNames()).containsExactly("reloj");
        } finally {
            view.dispose();
        }
    }

    @Test
    void showInCircuitSelectsTheWiresOfTheNetAndBlinksThem() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(4, 1), new GridPoint(8, 1));
        openDocument.execute(new AddElementsCommand(List.of(wire)));
        EditorView view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        WaveformFeatures features = new WaveformFeatures(view, NO_ANSWER, (parent, message) -> messages.add(message));
        try {
            String probeId = features.actions().addWire(wire.id()).addedIds().get(0);

            features.showInCircuit(probeId);

            assertThat(view.canvas().selection().ids()).containsExactly(wire.id());
            assertThat(features.blink().isBlinking()).isTrue();
        } finally {
            features.dispose();
            view.dispose();
        }
    }

    @Test
    void eachViewTakesBackExactlyItsListeners() {
        int probeListeners = openDocument.document().probeListenerCount();
        int changeListeners = openDocument.changeListenerCount();
        int appearanceListeners = appearance.listenerCount();

        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        assertThat(openDocument.document().probeListenerCount()).isEqualTo(probeListeners + 1);
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);
        assertThat(openDocument.document().probeListenerCount()).isEqualTo(probeListeners + 2);

        second.dispose();
        assertThat(openDocument.document().probeListenerCount()).isEqualTo(probeListeners + 1);
        assertThat(texts(second.menu(MenuId.VIEW))).doesNotContain("Formas de onda");
        assertThat(second.bottomDock().content()).isEmpty();
        first.dispose();
        assertThat(openDocument.document().probeListenerCount()).isEqualTo(probeListeners);
        assertThat(openDocument.changeListenerCount()).isEqualTo(changeListeners);
        assertThat(appearance.listenerCount()).isEqualTo(appearanceListeners);
    }

    @Test
    void aSelectionOfOnlyLabelsOffersNoWaveformItem() {
        TextLabel first = new TextLabel(openDocument.document().newLabelId(), new GridPoint(0, 0), "uno");
        TextLabel second = new TextLabel(openDocument.document().newLabelId(), new GridPoint(10, 0), "dos");
        openDocument.execute(new AddElementsCommand(List.of(first, second)));
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            view.canvas().selection().selectAll(openDocument.document());
            JPopupMenu menu = view.contextMenu(Optional.of(first), new Point2D.Double(1, 1));
            for (java.awt.Component child : menu.getComponents()) {
                if (child instanceof JMenuItem menuItem) {
                    assertThat(menuItem.getText()).isNotEqualTo("Ver en formas de onda");
                }
            }
        } finally {
            view.dispose();
        }
    }

    @Test
    void anAddThatFoundNothingLeavesThePanelClosed() {
        EditorView view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        WaveformFeatures features = new WaveformFeatures(view, NO_ANSWER, (parent, message) -> messages.add(message));
        try {
            features.afterAdd(new ProbeActions.AddResult(List.of(), Optional.empty(), false));

            assertThat(view.bottomDock().isDockShowing()).isFalse();
            assertThat(messages).isEmpty();
        } finally {
            features.dispose();
            view.dispose();
        }
    }
}
