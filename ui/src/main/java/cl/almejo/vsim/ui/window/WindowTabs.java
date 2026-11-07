package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.app.DocumentWindows;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.session.TabState;
import cl.almejo.vsim.ui.session.WindowSession;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.window.panes.RememberedSplit;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import cl.almejo.vsim.ui.window.tabs.TabMark;
import cl.almejo.vsim.ui.window.tabs.TabStrip;
import cl.almejo.vsim.ui.window.tabs.TabStripModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The tabs of one circuit window (spec §4.1–4.6): a {@link TabStrip} of groups (one per open document: its main tabs,
 * then its component tabs) above a card area with one {@link EditorView} per tab. The active tab's toolbar hangs in
 * the slot above the strip and its menu bar on the window ({@link Host#showMenuBar}); focus goes to its canvas.
 * Closing follows spec §4.5: a component tab just goes; the last main tab of a group closes the group, asking to save
 * once when this window is the document's last holder; a window left without tabs closes. Groups move as a unit,
 * within the strip, to another window (merging into that window's group of the same document) or out to a new one.
 * The frame ({@code CircuitWindow}) is the host: it owns the menu bar, the save question and the application. EDT only.
 */
public final class WindowTabs extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowTabs.class);
    /** Ctrl+AvPág / Ctrl+RePág: the other keys of «Pestaña siguiente / anterior», fixed (spec §4.2). */
    private static final KeyStroke NEXT_TAB_PAGE = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN,
            InputEvent.CTRL_DOWN_MASK);
    private static final KeyStroke PREVIOUS_TAB_PAGE = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP,
            InputEvent.CTRL_DOWN_MASK);

    /** What the tabs need from their window and the application. */
    public interface Host {

        /** Hang {@code menuBar} (the active tab's) on the window. */
        void showMenuBar(JMenuBar menuBar);

        boolean isMaximized();

        /** Whether no other window holds a tab of {@code document}. */
        boolean isLastHolder(OpenDocument document);

        /** Asks to save a modified {@code document}; false means the user cancelled. */
        boolean confirmSave(OpenDocument document);

        /** A tab of {@code document} was added here or removed (the window holds it; main tabs may renumber). */
        void tabsChanged(OpenDocument document);

        /** The group of {@code document} is about to go (its views still show it). */
        void groupClosing(OpenDocument document);

        /** The group of {@code document} went: this window no longer holds it. */
        void groupClosed(OpenDocument document);

        /** No tab is left: the window closes. */
        void emptied();

        /** The active tab changed, or its title did. */
        void activeTabChanged();

        /** The number of main tab {@code mainTabIndex} of {@code document} here among all its main tabs; 0 alone. */
        int viewNumber(OpenDocument document, int mainTabIndex);

        /** A group was dropped outside every strip: it opens in a new window at {@code screenPoint}. */
        void droppedOutside(OpenDocument document, Point screenPoint);

        /** Every tab of every window, grouped by window (the Ventana list and «▾»). */
        List<WindowMenu.Entry> everyTab();

        /** Where the AI works now: the robot on the tabs (fix round D, item 5n). None by default. */
        default PlaceMarks placeMarks() {
            return PlaceMarks.NONE;
        }
    }

    /** A tab as it travels to another window: what it shows and where its view was. */
    private record MovedTab(Optional<String> definitionId, OpenDocument place, ViewState view, boolean active) {
    }

    /**
     * One tab: its view on its place, and what opens its view the first time it shows. A component tab owns its body
     * place: the place is closed (its working copy and document listener released) when the tab goes.
     */
    private static final class TabEntry {
        private final String id;
        private final OpenDocument document;
        private final Optional<String> definitionId;
        private final OpenDocument place;
        private final EditorView view;
        private final InitialView initialView;
        private final Runnable initialViewWatch;
        private final RememberedSplit rememberedSplit;
        private Optional<ViewState> initialViewState;
        private boolean initialViewShown;
        /** Stops hearing that the definition left the circuit; nothing to stop for a main tab. */
        private Runnable bodyRemovedRemover = () -> {
        };
        /** Stops keeping the window's tabs in step with this tab's left column. */
        private Runnable foldListenerRemover = () -> {
        };
        /** An entered tab opens on the whole body («Ver todo»), travelling there from its initial view. */
        private boolean fitsBodyWhenShown;
        /** When it was last activated (a counter): re-entering focuses the most recent tab of a definition. */
        private long lastActivation;
        /** The definition's last known name: the notice still names it once it left the circuit. */
        private String definitionName;

        private TabEntry(String id, OpenDocument document, Optional<String> definitionId, OpenDocument place,
                         EditorView view, InitialView initialView, Runnable initialViewWatch,
                         RememberedSplit rememberedSplit, Optional<ViewState> initialViewState) {
            this.id = id;
            this.document = document;
            this.definitionId = definitionId;
            this.place = place;
            this.view = view;
            this.initialView = initialView;
            this.initialViewWatch = initialViewWatch;
            this.rememberedSplit = rememberedSplit;
            this.initialViewState = initialViewState;
            this.definitionName = definitionId.map(definition -> nameOf(document, definition)).orElse("");
        }

        private boolean isMain() {
            return definitionId.isEmpty();
        }

        private boolean shows(String definition) {
            return definitionId.filter(definition::equals).isPresent();
        }
    }

    private final Host host;
    private final Function<OpenDocument, EditorView> viewFactory;
    private final SplitLayoutStore splitStore;
    private final TabStripModel model = new TabStripModel();
    private final TabStrip strip;
    private final JPanel toolBarSlot = new JPanel(new BorderLayout());
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Map<String, TabEntry> entries = new LinkedHashMap<>();
    private final Map<OpenDocument, Runnable> followedDocuments = new HashMap<>();
    private int nextTabNumber;
    private long activations;
    private String shownId;
    private boolean windowOpened;
    private boolean disposed;
    /** The left-column panels folded in this window: every tab shows the same (spec §5.3), and the session keeps it. */
    private Set<LeftColumn.Section> foldedPanels = Set.of();
    private boolean foldingPanels;
    /** The right panel of this window: every tab shows it the same (open, section, width). */
    private InspectorState inspectorState = InspectorState.initial();
    private boolean spreadingInspector;
    private final KeyEventDispatcher tabKeys = this::takeTabKey;
    /** Where the AI works: the robot on the tabs, the Ventana list and «▾». */
    private final PlaceMarks placeMarks;
    private final Runnable placeMarksListenerRemover;

    /**
     * @param viewFactory makes the editor of a tab on a place (the window gives its services and itself as the
     *                    view's {@link WindowCommands})
     * @param splitStore  where tabs remember their split per place (file, and definition for a component tab)
     * @param animate     whether folding animates now (the «Animación» preference)
     */
    public WindowTabs(Host host, Function<OpenDocument, EditorView> viewFactory, SplitLayoutStore splitStore,
                      BooleanSupplier animate) {
        super(new BorderLayout());
        this.host = Objects.requireNonNull(host, "host");
        this.viewFactory = Objects.requireNonNull(viewFactory, "viewFactory");
        this.splitStore = Objects.requireNonNull(splitStore, "splitStore");
        this.strip = new TabStrip(model, animate);
        JPanel north = new JPanel(new BorderLayout());
        north.add(toolBarSlot, BorderLayout.NORTH);
        north.add(strip, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
        model.addListener(this::modelChanged);
        strip.onActivate(model::activate);
        strip.onClose(tabId -> Optional.ofNullable(entries.get(tabId)).ifPresent(entry -> closeTab(entry.view)));
        strip.onFoldToggle(document -> model.fold(document, !model.isFolded(document)));
        strip.onGroupDropped(this::groupDropped);
        strip.onListRequested(this::showTabList);
        this.placeMarks = host.placeMarks();
        strip.setMarks(this::markOf);
        this.placeMarksListenerRemover = placeMarks.addListener(strip::marksChanged);
        bindTabKeys();
    }

    /**
     * A theme change updates the window's component tree; the toolbars and menu bars of the tabs not shown hang
     * nowhere, so they are updated here too.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (entries == null) {
            return; // called by JPanel's constructor, before this class's fields exist
        }
        for (TabEntry entry : entries.values()) {
            if (entry.view.toolBar().getParent() == null) {
                SwingUtilities.updateComponentTreeUI(entry.view.toolBar());
            }
            if (entry.view.menuBar().getParent() == null) {
                SwingUtilities.updateComponentTreeUI(entry.view.menuBar());
            }
        }
    }

    /**
     * Ctrl+Tab and Ctrl+Mayús+Tab are the window's (spec §4.2), not Swing's focus traversal, which keeps Tab and
     * Mayús+Tab; Ctrl+AvPág and Ctrl+RePág cycle the tabs too. The components' own Ctrl+Tab bindings are passed over
     * by {@link #tabKeys()}.
     */
    private void bindTabKeys() {
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Set.of(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, 0)));
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                Set.of(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));
    }

    /**
     * Takes «Pestaña siguiente» / «Pestaña anterior» (Ctrl+Tab, Ctrl+Mayús+Tab, or what the user bound them to) and
     * Ctrl+AvPág / Ctrl+RePág before any component of this window sees them: split panes bind Ctrl+Tab to leave
     * themselves and tabbed panes Ctrl+Tab and Ctrl+AvPág to cycle their own tabs, and both win over a
     * menu accelerator or a binding of this panel. Installed while the window shows. Package-private for tests.
     */
    KeyEventDispatcher tabKeys() {
        return tabKeys;
    }

    private boolean takeTabKey(KeyEvent event) {
        Optional<TabEntry> active = activeEntry();
        if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed() || active.isEmpty()
                || !(event.getSource() instanceof java.awt.Component source)
                || !SwingUtilities.isDescendingFrom(source, this)) {
            return false;
        }
        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
        EditorActions actions = active.get().view.actions();
        if (stroke.equals(NEXT_TAB_PAGE) || stroke.equals(actions.nextTab().getValue(Action.ACCELERATOR_KEY))) {
            nextTab();
        } else if (stroke.equals(PREVIOUS_TAB_PAGE)
                || stroke.equals(actions.previousTab().getValue(Action.ACCELERATOR_KEY))) {
            previousTab();
        } else {
            return false;
        }
        event.consume();
        return true;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(tabKeys);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(tabKeys);
        super.removeNotify();
    }

    // --- adding tabs ---

    /** A new main tab of {@code document} (its group is made when it has none), activated. */
    public EditorView addTab(OpenDocument document, Optional<ViewState> initialView) {
        return add(document, Optional.empty(), document, initialView);
    }

    /**
     * A new component tab of definition {@code definitionId} on {@code place} (the body place of {@code document}),
     * at the end of the document's group, activated. The group must exist: a component tab is never alone.
     */
    public EditorView addComponentTab(OpenDocument document, String definitionId, OpenDocument place,
                                      Optional<ViewState> initialView) {
        if (model.group(document).isEmpty()) {
            throw new IllegalStateException("a component tab needs its circuit's group in the window");
        }
        return add(document, Optional.of(definitionId), place, initialView);
    }

    private EditorView add(OpenDocument document, Optional<String> definitionId, OpenDocument place,
                           Optional<ViewState> initialView) {
        TabEntry entry = createEntry(document, definitionId, place, initialView);
        model.add(new TabStripModel.Tab(entry.id, document, definitionId, document.displayName(), false));
        model.activate(entry.id);
        host.tabsChanged(document);
        refreshTitles();
        return entry.view;
    }

    private TabEntry createEntry(OpenDocument document, Optional<String> definitionId, OpenDocument place,
                                 Optional<ViewState> initialView) {
        String id = "tab" + ++nextTabNumber;
        EditorView view = viewFactory.apply(place);
        InitialView watcher = new InitialView(() -> showInitialView(id));
        Runnable watch = watchForTheInitialView(view, watcher, host::isMaximized);
        // a component tab keeps its own split, per (file, definition) (spec §10.6)
        RememberedSplit split = new RememberedSplit(splitStore, view.canvasPanes(), document::path, definitionId,
                Delay.swing());
        new WindowMenu(view.menu(MenuId.WINDOW), host::everyTab);
        TabEntry entry = new TabEntry(id, document, definitionId, place, view, watcher, watch, split, initialView);
        definitionId.filter(definition -> place.isBody()).ifPresent(definition -> {
            Runnable listener = () -> definitionGone(document, definition);
            place.addBodyRemovedListener(listener);
            entry.bodyRemovedRemover = () -> place.removeBodyRemovedListener(listener);
        });
        foldedPanels.forEach(section -> view.leftColumn().setFolded(section, true));
        entry.foldListenerRemover = view.leftColumn().addFoldListener(() -> panelsFolded(view));
        view.inspector().restore(inspectorState);
        view.inspector().onStateChange(() -> inspectorChanged(view));
        entries.put(id, entry);
        cards.add(view, id);
        follow(document);
        return entry;
    }

    /** Titles and «●» follow the document (saved, renamed, modified). */
    private void follow(OpenDocument document) {
        if (!followedDocuments.containsKey(document)) {
            Runnable listener = this::refreshTitles;
            document.addChangeListener(listener);
            followedDocuments.put(document, () -> document.removeChangeListener(listener));
        }
    }

    private void stopFollowing(OpenDocument document) {
        Optional.ofNullable(followedDocuments.remove(document)).ifPresent(Runnable::run);
    }

    /**
     * The user's own moves on any pane keep the view they chose; a resize of the canvas area can be the maximized
     * layout arriving (InitialView only acts on the first one after opening). The area is watched, not each pane: a
     * split, even the remembered one applied at opening, resizes the panes but not the area, and must not use up
     * the wait for the maximized size. Returns what stops watching. Package-private for tests.
     */
    static Runnable watchForTheInitialView(EditorView view, InitialView initialView, BooleanSupplier maximized) {
        Runnable paneWatchRemover = view.forEachPane(pane -> watchForTheUsersView(pane, initialView));
        ComponentAdapter resizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                initialView.canvasResized(maximized.getAsBoolean());
            }
        };
        view.canvasPanes().addComponentListener(resizeListener);
        return () -> {
            paneWatchRemover.run();
            view.canvasPanes().removeComponentListener(resizeListener);
        };
    }

    /** The user's own moves on {@code pane} keep the view they chose. Returns what stops watching the pane. */
    private static Runnable watchForTheUsersView(CircuitCanvas pane, InitialView initialView) {
        Runnable viewListenerRemover = pane.addViewListener(initialView::viewChanged);
        // Pans by drag, wheel or autoscroll do not reach the view listeners; any press or wheel turn counts as one.
        MouseAdapter pressListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                initialView.userInteracted();
            }
        };
        MouseWheelListener wheelListener = event -> initialView.userInteracted();
        pane.addMouseListener(pressListener);
        pane.addMouseWheelListener(wheelListener);
        return () -> {
            viewListenerRemover.run();
            pane.removeMouseListener(pressListener);
            pane.removeMouseWheelListener(wheelListener);
        };
    }

    // --- entering (spec §4.4) ---

    /**
     * Enters definition {@code definitionId} of {@code document}: the group's tab of that definition most recently
     * active is activated and shows {@code contextPath} (empty: «Sin valores») — the others keep theirs; without one a
     * new component tab opens on a body place, at the end of the group, on the whole body (travelling there from
     * {@code from} when given). The group is unfolded, and brought here first when this window has none.
     *
     * @throws IllegalArgumentException when the document has no such definition
     */
    public EditorView enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                            Optional<ViewState> from) {
        if (model.group(document).isEmpty()) {
            addTab(document, Optional.empty());
        }
        model.fold(document, false);
        Optional<TabEntry> existing = entriesOf(document).stream().filter(entry -> entry.shows(definitionId))
                .max(Comparator.comparingLong(entry -> entry.lastActivation));
        if (existing.isPresent()) {
            existing.get().place.setContextPath(contextPath);
            model.activate(existing.get().id);
            return existing.get().view;
        }
        OpenDocument place = document.openBody(definitionId, contextPath);
        EditorView view = addComponentTab(document, definitionId, place, from);
        entryOf(view).ifPresent(entry -> entry.fitsBodyWhenShown = true);
        return view;
    }

    /**
     * Brings a place of {@code document}'s group to the front: its main tab when {@code definitionId} is empty (the most
     * recently active one; the group is added, and unfolded, when needed), else the component tab entered as
     * {@link #enter} does.
     */
    public EditorView showPlace(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath) {
        if (definitionId.isPresent()) {
            return enter(document, definitionId.get(), contextPath, Optional.empty());
        }
        Optional<TabEntry> main = entriesOf(document).stream().filter(TabEntry::isMain)
                .max(Comparator.comparingLong(entry -> entry.lastActivation));
        if (main.isEmpty()) {
            return addTab(document, Optional.empty());
        }
        model.fold(document, false);
        model.activate(main.get().id);
        return main.get().view;
    }

    /**
     * Brings to the front the place where a step of the history was made ({@link WindowCommands#showPlaceOfStep}): the
     * main tab, or the most recently active tab of the definition as it is (its «Valores de» stays), or a new component
     * tab on {@code contextPath} when none is open.
     */
    public EditorView showPlaceOfStep(OpenDocument document, Optional<String> definitionId,
                                      Optional<String> contextPath) {
        if (definitionId.isEmpty()) {
            return showPlace(document, Optional.empty(), Optional.empty());
        }
        Optional<TabEntry> open = entriesOf(document).stream().filter(entry -> entry.shows(definitionId.get()))
                .max(Comparator.comparingLong(entry -> entry.lastActivation));
        if (open.isEmpty()) {
            return enter(document, definitionId.get(), contextPath, Optional.empty());
        }
        model.fold(document, false);
        model.activate(open.get().id);
        return open.get().view;
    }

    /**
     * Shows part {@code part} of {@code document} where it is: the place {@link #showPlace} brings to the front, with the
     * part selected and brought into view. F8 and the Problemas list (spec §6.7), «Ver la pieza» of the place bar and of
     * the circuit tree (spec §5) all come here. Returns the tab's view.
     */
    public EditorView goToPart(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                               ElementId part) {
        EditorView view = showPlace(document, definitionId, contextPath);
        SeePiece.selectAndTravel(view, part);
        return view;
    }

    /**
     * The definition left the circuit (an undo, «Actualizar desde la librería», the AI): its tabs here close, and the
     * group's main tab says so once (spec §4.5).
     */
    private void definitionGone(OpenDocument document, String definitionId) {
        List<TabEntry> gone = entriesOf(document).stream().filter(entry -> entry.shows(definitionId)).toList();
        if (gone.isEmpty()) {
            return; // another tab of the definition already closed them all
        }
        String name = gone.getFirst().definitionName;
        gone.forEach(this::removeTab);
        entriesOf(document).stream().filter(TabEntry::isMain).findFirst().ifPresent(main ->
                main.view.statusBar().showNotice(Messages.format("inside.definitionGone", name)));
    }

    private static String nameOf(OpenDocument document, String definitionId) {
        return document.document().definition(definitionId).map(ComponentDefinition::name).orElse(definitionId);
    }

    // --- what the window reads ---

    public TabStripModel model() {
        return model;
    }

    /** Every view, in the strip's order (hidden component tabs of folded groups included). */
    public List<EditorView> views() {
        return model.tabs().stream().map(tab -> entries.get(tab.id()).view).toList();
    }

    /** The active tab's view; there is one while the window has tabs. */
    public EditorView activeView() {
        return activeEntry().orElseThrow(() -> new IllegalStateException("the window has no tabs")).view;
    }

    /** The document of the active tab's group (a component tab's circuit). */
    public OpenDocument activeDocument() {
        return activeEntry().orElseThrow(() -> new IllegalStateException("the window has no tabs")).document;
    }

    /** The documents this window holds, in the strip's order. */
    public List<OpenDocument> documents() {
        return model.groups().stream().map(group -> (OpenDocument) group.document()).toList();
    }

    /** The main and component tabs' views of {@code document}'s group, in order. */
    public List<EditorView> viewsOf(OpenDocument document) {
        return entriesOf(document).stream().map(entry -> entry.view).toList();
    }

    /** The first tab showing {@code place} (a document's main circuit, or a body place). */
    public Optional<EditorView> viewOf(OpenDocument place) {
        return model.tabs().stream().map(tab -> entries.get(tab.id()))
                .filter(entry -> entry.place == place).map(entry -> entry.view).findFirst();
    }

    public int mainTabCount(OpenDocument document) {
        return (int) entriesOf(document).stream().filter(TabEntry::isMain).count();
    }

    /** The index of the active tab among its document's main tabs here (0 for a component tab: its first main). */
    public int activeMainTabIndex() {
        Optional<TabEntry> active = activeEntry();
        if (active.isEmpty() || !active.get().isMain()) {
            return 0;
        }
        return entriesOf(active.get().document).stream().filter(TabEntry::isMain).toList().indexOf(active.get());
    }

    public boolean isEmpty() {
        return model.isEmpty();
    }

    /** The slot above the strip where the active tab's toolbar hangs. */
    public JComponent toolBarSlot() {
        return toolBarSlot;
    }

    /** Every tab's inline editor: an open inline edit is finished before a close asks to save. */
    public List<InlineTextEditor> inlineEditors() {
        return views().stream().map(EditorView::inlineEditor).toList();
    }

    /** One entry per tab for the Ventana list; {@code current} marks the active tab when this is the asking window. */
    public List<WindowMenu.Entry> menuEntries(boolean currentWindow, Runnable focusWindow) {
        String activeId = model.active().map(TabStripModel.Tab::id).orElse("");
        return model.tabs().stream()
                .map(tab -> new WindowMenu.Entry(tab.title(), currentWindow && tab.id().equals(activeId), () -> {
                    focusWindow.run();
                    model.activate(tab.id());
                }, false, markOf(tab)))
                .toList();
    }

    /** The AI's mark on {@code tab}'s place: its circuit, or its component with the piece its «Valores de» shows. */
    private Optional<TabMark> markOf(TabStripModel.Tab tab) {
        return Optional.ofNullable(entries.get(tab.id()))
                .flatMap(entry -> placeMarks.markOf(entry.document, entry.definitionId, entry.place.contextPath()));
    }

    private Optional<TabEntry> activeEntry() {
        return model.active().map(tab -> entries.get(tab.id()));
    }

    private Optional<TabEntry> entryOf(EditorView view) {
        return entries.values().stream().filter(entry -> entry.view == view).findFirst();
    }

    private List<TabEntry> entriesOf(OpenDocument document) {
        return model.group(document)
                .map(group -> group.tabs().stream().map(tab -> entries.get(tab.id())).toList())
                .orElse(List.of());
    }

    // --- activation ---

    public void activate(EditorView view) {
        entryOf(view).ifPresent(entry -> model.activate(entry.id));
    }

    public void nextTab() {
        model.next();
    }

    public void previousTab() {
        model.previous();
    }

    public void foldGroup(OpenDocument document, boolean folded) {
        model.fold(document, folded);
    }

    public boolean isFolded(OpenDocument document) {
        return model.isFolded(document);
    }

    private void modelChanged() {
        String activeId = model.active().map(TabStripModel.Tab::id).orElse(null);
        if (!Objects.equals(activeId, shownId)) {
            shownId = activeId;
            show(Optional.ofNullable(activeId).map(entries::get));
        }
        host.activeTabChanged();
    }

    /** Shows {@code entry}'s card, hangs its toolbar and menu bar, and gives its canvas the focus. */
    private void show(Optional<TabEntry> shown) {
        toolBarSlot.removeAll();
        if (shown.isEmpty()) {
            toolBarSlot.revalidate();
            return;
        }
        TabEntry entry = shown.get();
        entry.lastActivation = ++activations;
        placeMarks.visited(entry.document, entry.definitionId, entry.place.contextPath());
        cardLayout.show(cards, entry.id);
        entry.view.tabShown();
        toolBarSlot.add(entry.view.toolBar(), BorderLayout.CENTER);
        toolBarSlot.revalidate();
        toolBarSlot.repaint();
        host.showMenuBar(entry.view.menuBar());
        if (windowOpened && !entry.initialViewShown) {
            // after the card's layout, and after whoever added the tab asked for its remembered split
            SwingUtilities.invokeLater(() -> showInitialViewOnce(entry));
        }
        SwingUtilities.invokeLater(() -> {
            if (entry.id.equals(shownId)) {
                entry.view.canvas().requestFocusInWindow();
            }
        });
    }

    /** The window opened: the active tab shows its initial view (again later if a maximized layout arrives). */
    public void windowOpened(boolean maximized) {
        windowOpened = true;
        activeEntry().ifPresent(entry -> {
            entry.initialViewShown = true;
            entry.initialView.opened(maximized);
            entry.view.canvas().requestFocusInWindow();
        });
    }

    private void showInitialViewOnce(TabEntry entry) {
        if (!entry.initialViewShown && entries.containsKey(entry.id)) {
            entry.initialViewShown = true;
            entry.initialView.opened(false);
        }
    }

    private void showInitialView(String tabId) {
        TabEntry entry = entries.get(tabId);
        if (entry == null) {
            return;
        }
        CircuitCanvas canvas = entry.view.canvas();
        entry.initialViewState.ifPresentOrElse(canvas::applyViewState, () -> {
            if (!entry.fitsBodyWhenShown) {
                canvas.centerCircuit();
            }
        });
        if (entry.fitsBodyWhenShown) {
            // «Ver todo» on the body; from where the instance was, it travels when «Animación» is on
            entry.initialViewState = Optional.empty();
            CircuitBounds.of(entry.place.document(), entry.place.registry()).ifPresent(canvas::zoomToFit);
        }
        entry.rememberedSplit.showRemembered();
    }

    /** {@code view} opens at {@code state} instead of centering the circuit; call it before the tab is first shown. */
    public void setInitialViewState(EditorView view, ViewState state) {
        entryOf(view).ifPresent(entry -> entry.initialViewState = Optional.of(state));
    }

    /**
     * {@code view} shows a file being opened, not another view of an open one: it opens split as that file's window
     * was last time (when it fits). Call it right after adding the tab.
     */
    public void restoreRememberedSplit(EditorView view) {
        entryOf(view).ifPresent(entry -> entry.rememberedSplit.restoreWhenShown());
    }

    /** {@code document} was just written to {@code target} (before that {@code previous}): its splits follow. */
    public void saved(OpenDocument document, Optional<Path> previous, Path target) {
        entriesOf(document).forEach(entry -> entry.rememberedSplit.saved(previous, target));
    }

    // --- the session ---

    /**
     * What the session remembers of this window (spec §4.7): every tab of a saved circuit in the strip's order (its
     * place, the instance a component tab shows and where it looks), the active one and the folded groups, with
     * {@code bounds}; untitled circuits are left out (they come back through recovery). Empty when no tab is left.
     */
    public Optional<WindowSession> session(WindowState bounds) {
        String activeId = model.active().map(TabStripModel.Tab::id).orElse("");
        List<TabState> tabs = new ArrayList<>();
        int activeTab = 0;
        for (TabStripModel.Tab tab : model.tabs()) {
            TabEntry entry = entries.get(tab.id());
            Optional<Path> path = entry.document.path();
            if (path.isEmpty()) {
                continue;
            }
            if (tab.id().equals(activeId)) {
                activeTab = tabs.size();
            }
            tabs.add(tabState(entry, path.get()));
        }
        if (tabs.isEmpty()) {
            return Optional.empty();
        }
        List<Path> folded = documents().stream().filter(model::isFolded)
                .flatMap(document -> document.path().stream()).toList();
        Set<String> panels = foldedPanels.stream().map(WindowTabs::sessionName).collect(Collectors.toSet());
        return Optional.of(new WindowSession(bounds, tabs, activeTab, folded, panels,
                Optional.of(inspectorState)));
    }

    /** A tab never shown yet still looks where it was going to open. */
    private static TabState tabState(TabEntry entry, Path path) {
        ViewState view = entry.initialViewShown ? entry.view.canvas().viewState()
                : entry.initialViewState.orElseGet(() -> entry.view.canvas().viewState());
        return entry.definitionId
                .map(definitionId -> TabState.component(path, definitionId, entry.place.definitionName(),
                        entry.place.contextPath(), view))
                .orElseGet(() -> TabState.main(path, view));
    }

    /**
     * Brings back a window of the session (spec §4.7): its groups in order (the main tabs first, each opening where it
     * looked and split as it was), then the component tabs on body places of their documents, then the active tab and
     * the folds. {@code documents} gives the open document of a file; the tabs of a file it has none for are left
     * out (the file did not open, and that was told). A component tab whose definition is gone from its document is
     * left out and returned, to be told once; one whose instance is gone shows the first remaining one, or «Sin
     * valores» ({@link OpenDocument#openBody}).
     *
     * @return the component tabs left out because their definition is gone
     */
    public List<TabState> restore(WindowSession session, Function<Path, Optional<OpenDocument>> documents) {
        useInspectorState(session.inspector().orElseGet(InspectorState::initial));
        foldPanels(session.foldedPanels().stream()
                .flatMap(name -> Arrays.stream(LeftColumn.Section.values())
                        .filter(section -> sessionName(section).equals(name)))
                .collect(Collectors.toSet()));
        List<TabState> tabs = session.tabs();
        List<Optional<EditorView>> restored = new ArrayList<>();
        tabs.forEach(tab -> restored.add(Optional.empty()));
        for (int index = 0; index < tabs.size(); index++) {
            TabState tab = tabs.get(index);
            if (tab.isMain()) {
                restored.set(index, documents.apply(tab.path()).map(document -> restoredTab(document, tab)));
            }
        }
        List<TabState> skipped = new ArrayList<>();
        for (int index = 0; index < tabs.size(); index++) {
            TabState tab = tabs.get(index);
            Optional<OpenDocument> document = documents.apply(tab.path());
            if (tab.isMain() || document.isEmpty()) {
                continue;
            }
            String definitionId = tab.definitionId().orElseThrow();
            if (document.get().document().definition(definitionId).isEmpty()) {
                skipped.add(tab);
                continue;
            }
            if (model.group(document.get()).isEmpty()) {
                restoredTab(document.get(), TabState.main(tab.path(), tab.view())); // never a component tab alone
            }
            OpenDocument place = document.get().openBody(definitionId, tab.contextPath());
            EditorView view = addComponentTab(document.get(), definitionId, place, Optional.of(tab.view()));
            restoreRememberedSplit(view);
            restored.set(index, Optional.of(view));
        }
        restored.get(session.activeTab()).or(() -> views().stream().findFirst()).ifPresent(this::activate);
        session.foldedDocuments().forEach(path -> documents.apply(path)
                .filter(document -> model.group(document).isPresent())
                .ifPresent(document -> model.fold(document, true)));
        return skipped;
    }

    /** How the session names a left-column panel: {@code circuit}, {@code palette}. */
    private static String sessionName(LeftColumn.Section section) {
        return section.name().toLowerCase(Locale.ROOT);
    }

    /** A tab's left column folded or unfolded a panel: the window's other tabs follow. */
    private void panelsFolded(EditorView view) {
        if (!foldingPanels) {
            foldPanels(view.leftColumn().folded());
        }
    }

    /** A tab's right panel opened, closed, switched section or was given another width: the window's other tabs follow. */
    private void inspectorChanged(EditorView view) {
        if (!spreadingInspector) {
            useInspectorState(view.inspector().state());
        }
    }

    /** {@code state} becomes this window's right panel, in every tab. */
    private void useInspectorState(InspectorState state) {
        inspectorState = state;
        spreadingInspector = true; // the tabs' own state listeners must not answer the changes made here
        try {
            entries.values().forEach(entry -> entry.view.inspector().restore(state));
        } finally {
            spreadingInspector = false;
        }
    }

    /** {@code folded} becomes this window's folded panels, in every tab. */
    private void foldPanels(Set<LeftColumn.Section> folded) {
        foldedPanels = Set.copyOf(folded);
        foldingPanels = true; // the tabs' own fold listeners must not answer the changes made here
        try {
            for (TabEntry entry : entries.values()) {
                for (LeftColumn.Section section : LeftColumn.Section.values()) {
                    entry.view.leftColumn().setFolded(section, foldedPanels.contains(section));
                }
            }
        } finally {
            foldingPanels = false;
        }
    }

    private EditorView restoredTab(OpenDocument document, TabState tab) {
        EditorView view = addTab(document, Optional.of(tab.view()));
        restoreRememberedSplit(view);
        return view;
    }

    // --- titles ---

    /** Retitles every tab: «lrv32im», «lrv32im (2)», «Ram», «Ram (2)», with «●» for a modified main tab. */
    public void refreshTitles() {
        List<TabStripModel.Group> groups = model.groups();
        // circuits of this window that share a name are told apart by their folder or a number
        List<String> circuitNames = TabTitles.distinct(groups.stream()
                .map(group -> (OpenDocument) group.document())
                .map(document -> new TabTitles.Named(document.displayName(), document.path()))
                .toList());
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            TabStripModel.Group group = groups.get(groupIndex);
            OpenDocument document = (OpenDocument) group.document();
            List<TabStripModel.Tab> mains = group.mainTabs();
            for (int index = 0; index < mains.size(); index++) {
                TabStripModel.Tab tab = mains.get(index);
                model.update(new TabStripModel.Tab(tab.id(), document, tab.definitionId(),
                        DocumentWindows.viewTitle(circuitNames.get(groupIndex), host.viewNumber(document, index)),
                        document.isModified()));
            }
            Map<String, List<TabStripModel.Tab>> byDefinition = new LinkedHashMap<>();
            group.componentTabs().forEach(tab -> byDefinition
                    .computeIfAbsent(tab.definitionId().orElseThrow(), key -> new ArrayList<>()).add(tab));
            byDefinition.forEach((definitionId, tabs) -> {
                String name = nameOf(document, definitionId);
                for (int index = 0; index < tabs.size(); index++) {
                    TabStripModel.Tab tab = tabs.get(index);
                    if (document.document().definition(definitionId).isPresent()) {
                        entries.get(tab.id()).definitionName = name;
                    }
                    model.update(new TabStripModel.Tab(tab.id(), document, tab.definitionId(),
                            DocumentWindows.viewTitle(name, tabs.size() > 1 ? index + 1 : 0), false));
                }
            });
        }
    }

    // --- closing ---

    /**
     * Closes {@code view}'s tab (spec §4.5): a component tab or one of several main tabs just goes; the last main tab
     * closes its group ({@link #closeGroup}).
     */
    public void closeTab(EditorView view) {
        Optional<TabEntry> found = entryOf(view);
        if (found.isEmpty()) {
            return;
        }
        TabEntry entry = found.get();
        if (!entry.isMain() || mainTabCount(entry.document) > 1) {
            removeTab(entry);
            return;
        }
        closeGroup(entry.document);
    }

    /**
     * Closes {@code document}'s group: asks to save once when this window is its last holder (after finishing the
     * group's inline edits); false when the user cancelled. The window's only group closes the window.
     */
    public boolean closeGroup(OpenDocument document) {
        List<TabEntry> group = entriesOf(document);
        if (group.isEmpty()) {
            return true;
        }
        boolean confirmed = CloseSequence.confirm(group.stream().map(entry -> entry.view.inlineEditor()).toList(),
                () -> !host.isLastHolder(document) || host.confirmSave(document));
        if (!confirmed) {
            return false;
        }
        if (model.groups().size() == 1) {
            host.emptied(); // closing the window keeps today's rules (session, Recientes)
            return true;
        }
        removeGroupQuietly(document);
        return true;
    }

    /** «Cerrar los componentes de este circuito»: every component tab of the group goes; nothing is asked. */
    public void closeComponentTabs(OpenDocument document) {
        entriesOf(document).stream().filter(entry -> !entry.isMain()).forEach(this::removeTab);
    }

    private void removeTab(TabEntry entry) {
        model.remove(entry.id);
        disposeEntry(entry);
        host.tabsChanged(entry.document);
        refreshTitles();
    }

    /** The group goes without asking (it closed after the question, or it moved to another window). */
    private void removeGroupQuietly(OpenDocument document) {
        host.groupClosing(document);
        List<TabEntry> group = entriesOf(document);
        model.takeGroup(document);
        group.forEach(this::disposeEntry);
        stopFollowing(document);
        host.groupClosed(document);
        if (model.isEmpty()) {
            host.emptied();
        } else {
            refreshTitles();
        }
    }

    private void disposeEntry(TabEntry entry) {
        entries.remove(entry.id);
        release(entry);
        cards.remove(entry.view);
    }

    private static void release(TabEntry entry) {
        try {
            entry.rememberedSplit.close();
            entry.initialViewWatch.run();
            entry.bodyRemovedRemover.run();
            entry.foldListenerRemover.run();
            entry.view.dispose();
        } catch (RuntimeException exception) {
            // a broken extension disposer must not keep the tab, or the window, open
            LOGGER.error("Could not release the editor of {}", entry.document.displayName(), exception);
        } finally {
            if (entry.place.isBody()) {
                entry.place.close(); // the tab's own body place: its working copy and document listener go
            }
        }
    }

    /**
     * Releases every tab's editor; the window is closing. The views stay readable (the application keeps where each
     * file was in Recientes after the window disposed itself). Idempotent.
     */
    public void disposeAll() {
        if (disposed) {
            return;
        }
        disposed = true;
        placeMarksListenerRemover.run();
        entries.values().forEach(WindowTabs::release);
        List.copyOf(followedDocuments.keySet()).forEach(this::stopFollowing);
        toolBarSlot.removeAll();
    }

    // --- moving groups ---

    /** «Nueva pestaña del mismo circuito»: another tab on the active tab's place, opening where it shows now. */
    public EditorView newViewOfActivePlace() {
        TabEntry active = activeEntry().orElseThrow(() -> new IllegalStateException("the window has no tabs"));
        Optional<ViewState> state = Optional.of(active.view.canvas().viewState());
        return active.definitionId
                .map(definitionId -> addComponentTab(active.document, definitionId,
                        ownPlace(active.document, active.definitionId, active.place), state))
                .orElseGet(() -> addTab(active.document, state));
    }

    /**
     * Moves {@code document}'s group to {@code target} at {@code groupIndex} (spec §4.3): new views there on the same
     * places, where these showed; when {@code target} already has the document, only the component tabs join its
     * group. This window lets the group go, and closes when it was its last.
     */
    public void moveGroupTo(OpenDocument document, WindowTabs target, int groupIndex) {
        if (target == this) {
            model.moveGroup(document, groupIndex);
            return;
        }
        List<TabEntry> group = entriesOf(document);
        if (group.isEmpty()) {
            return;
        }
        String activeId = model.active().map(TabStripModel.Tab::id).orElse("");
        List<MovedTab> moved = group.stream().map(entry -> new MovedTab(entry.definitionId, entry.place,
                entry.view.canvas().viewState(), entry.id.equals(activeId))).toList();
        target.receiveGroup(document, moved, model.isFolded(document), groupIndex);
        removeGroupQuietly(document);
    }

    private void receiveGroup(OpenDocument document, List<MovedTab> moved, boolean folded, int groupIndex) {
        boolean merging = model.group(document).isPresent();
        List<TabStripModel.Tab> arriving = new ArrayList<>();
        String activate = null;
        for (MovedTab tab : moved) {
            if (merging && tab.definitionId().isEmpty()) {
                continue;
            }
            TabEntry entry = createEntry(document, tab.definitionId(),
                    ownPlace(document, tab.definitionId(), tab.place()), Optional.of(tab.view()));
            arriving.add(new TabStripModel.Tab(entry.id, document, tab.definitionId(), document.displayName(),
                    false));
            if (tab.active()) {
                activate = entry.id;
            }
        }
        model.putGroup(new TabStripModel.Group(document, arriving, folded), groupIndex);
        String firstMain = model.group(document).orElseThrow().mainTabs().get(0).id();
        model.activate(activate != null ? activate : firstMain);
        host.tabsChanged(document);
        refreshTitles();
    }

    /**
     * The place of another tab like one on {@code place}: the same place for a main tab; a new body place on the same
     * definition and context for a component tab, since each component tab owns (and closes) its own.
     */
    private static OpenDocument ownPlace(OpenDocument document, Optional<String> definitionId, OpenDocument place) {
        if (!place.isBody()) {
            return place;
        }
        return document.openBody(definitionId.orElseThrow(), place.contextPath());
    }

    /** What a drop of a dragged group means (spec §4.3). */
    public void groupDropped(TabStrip.GroupDrop drop) {
        OpenDocument document = (OpenDocument) drop.document();
        switch (drop.target()) {
            case TabStrip.InStrip inStrip -> model.moveGroup(document, inStrip.groupIndex());
            case TabStrip.OtherStrip otherStrip -> {
                WindowTabs target = (WindowTabs) SwingUtilities.getAncestorOfClass(WindowTabs.class,
                        otherStrip.strip());
                if (target != null) {
                    moveGroupTo(document, target, otherStrip.groupIndex());
                }
            }
            case TabStrip.Outside outside -> host.droppedOutside(document, outside.screenPoint());
        }
    }

    /** «▾»: every tab of every window, grouped by window. */
    private void showTabList() {
        JPopupMenu popup = new JPopupMenu();
        host.everyTab().forEach(entry -> popup.add(WindowMenu.itemFor(entry)));
        popup.show(strip, Math.max(0, strip.getWidth() - popup.getPreferredSize().width), strip.getHeight());
    }
}
