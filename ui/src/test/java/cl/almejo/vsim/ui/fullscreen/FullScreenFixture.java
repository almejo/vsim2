package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.PlaceMarks;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.WindowMenu;
import cl.almejo.vsim.ui.window.WindowTabs;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;

import javax.swing.JMenuBar;
import javax.swing.JRootPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A circuit window as presentation mode sees it, without a frame (none can be built headless): a root pane holding
 * the window's tabs, whose views run the given extensions and whose window commands give this window's
 * {@link FullScreenFeatures}; a recording screen mode and hand tickers.
 */
final class FullScreenFixture implements AutoCloseable {

    static final int WIDTH = 1280;
    static final int HEIGHT = 820;

    final AppPreferences preferences = AppPreferences.inMemory();
    final ManualFrameTicker barTicker = new ManualFrameTicker();
    final ManualFrameTicker noticeTicker = new ManualFrameTicker();
    final RecordingScreenMode screen = new RecordingScreenMode();
    final RecordingKeyDispatchers keyHooks = new RecordingKeyDispatchers();
    final JRootPane rootPane = new JRootPane();
    final WindowTabs tabs;
    final FullScreenFeatures features;
    private final List<OpenDocument> documents = new ArrayList<>();
    private final ComponentTypeRegistry registry;

    /** What the real screen mode would do to the frame, counted. */
    static final class RecordingScreenMode implements ScreenMode {
        int entered;
        int left;
        int closed;

        @Override
        public void enter() {
            entered++;
        }

        @Override
        public void leave() {
            left++;
        }

        @Override
        public void close() {
            closed++;
        }
    }

    FullScreenFixture(List<EditorExtension> extensions) {
        this(extensions, StandardComponentTypes.registry());
    }

    FullScreenFixture(List<EditorExtension> extensions, ComponentTypeRegistry registry) {
        this.registry = registry;
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), extensions);
        FullScreenFeatures[] holder = new FullScreenFeatures[1];
        WindowCommands window = new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }

            @Override
            public Optional<FullScreenFeatures> fullScreen() {
                return Optional.ofNullable(holder[0]);
            }
        };
        tabs = new WindowTabs(new Host(), place -> new EditorView(place, services, window),
                new SplitLayoutStore(preferences), () -> false);
        rootPane.setContentPane(tabs);
        features = new FullScreenFeatures(rootPane, tabs, screen, preferences, barTicker, noticeTicker,
                barTicker::now, keyHooks);
        holder[0] = features;
        rootPane.setSize(WIDTH, HEIGHT);
    }

    /** A new tab on a new empty circuit, laid out. */
    EditorView addTab() {
        OpenDocument document = OpenDocument.createEmpty(registry);
        documents.add(document);
        EditorView view = tabs.addTab(document, Optional.empty());
        layOut();
        return view;
    }

    /** Moves the shared clock (the bars' ticker's) and runs the frames of both tickers in between. */
    void advance(long milliseconds) {
        barTicker.advance(milliseconds);
        noticeTicker.advance(milliseconds);
    }

    /** Lays the window out as Swing would before painting (validate does nothing without a shown frame). */
    void layOut() {
        rootPane.setSize(WIDTH, HEIGHT);
        layOut(rootPane);
    }

    private static void layOut(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    @Override
    public void close() {
        features.dispose();
        tabs.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    /** The frame's part: the menu bar goes where presentation mode says. */
    private final class Host implements WindowTabs.Host {

        @Override
        public void showMenuBar(JMenuBar menuBar) {
            features.showMenuBar(menuBar);
        }

        @Override
        public boolean isMaximized() {
            return false;
        }

        @Override
        public boolean isLastHolder(OpenDocument document) {
            return true;
        }

        @Override
        public boolean confirmSave(OpenDocument document) {
            return true;
        }

        @Override
        public void tabsChanged(OpenDocument document) {
        }

        @Override
        public void groupClosing(OpenDocument document) {
        }

        @Override
        public void groupClosed(OpenDocument document) {
        }

        @Override
        public void emptied() {
        }

        @Override
        public void activeTabChanged() {
        }

        @Override
        public int viewNumber(OpenDocument document, int mainTabIndex) {
            return 0;
        }

        @Override
        public void droppedOutside(OpenDocument document, Point screenPoint) {
        }

        @Override
        public List<WindowMenu.Entry> everyTab() {
            return List.of();
        }

        @Override
        public void newTab() {
        }

        @Override
        public PlaceMarks placeMarks() {
            return PlaceMarks.NONE;
        }
    }
}
