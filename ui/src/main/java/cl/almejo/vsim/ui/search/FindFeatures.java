package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.search.CircuitSearch;
import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.core.search.SearchHit;
import cl.almejo.vsim.core.search.SearchProblem;
import cl.almejo.vsim.core.search.SearchQuery;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * «Buscar en el circuito» in one editor view: Editar ▸ «Buscar…» (Ctrl+F), «Buscar siguiente» (F3) and «Buscar
 * anterior» (Mayús+F3), the {@link FindBar} under the toolbar and the {@link FindHighlightOverlay} on the canvas.
 *
 * <p>Typing searches at once (the shared {@link CompiledQuery} of the core, with the «Aa» and «.*» preferences): every
 * match is highlighted and the current one is selected and travelled to ({@link CircuitCanvas#travelTo}). Enter / ↓ /
 * F3 go to the next match and Shift+Enter / ↑ / Mayús+F3 to the previous one, wrapping around; Esc or ✕ close the bar,
 * clear the highlights and give the focus back to the canvas, keeping the text. Reopening goes on from the match the
 * user was on: Ctrl+F shows it again, F3 / Mayús+F3 go to the one after / before it. While the bar is open every document change searches
 * again: the current match stays current while it still matches, else the one now in its place (the next one in
 * order) becomes current, without moving the view under the user's edits. Ctrl+F while the focus is inside a
 * component that has a filter of its own ({@link #FILTER_FOCUS_PROPERTY}, the component tree) focuses that filter.
 *
 * <p>Searching runs on the EDT: the core caps texts and gives each search of a regular expression one step budget for
 * all its texts, so even a pathological one costs a bounded few tens of milliseconds per keystroke (then the bar says
 * it is too costly and highlights nothing), and the bundled examples search in well under a millisecond.
 * {@link #dispose()} takes back everything. EDT only.
 */
public final class FindFeatures {

    /**
     * Client property of a component with a filter field of its own: a {@link Runnable} that focuses it. Ctrl+F with the
     * focus inside that component runs it instead of opening the bar.
     */
    public static final String FILTER_FOCUS_PROPERTY = "vsim.find.focusFilter";

    /** Why the matches are searched again: what the user typed or chose, or an edit of the document. */
    private enum Reason { QUERY, EDIT, QUIET_OPEN }

    /** Identifies a match across searches: its element and, inside it, the parts' ids (names may repeat). */
    private record HitKey(ElementId elementId, List<ElementId> partIds) {
        static HitKey of(SearchHit hit) {
            return new HitKey(hit.elementId(), hit.partIds());
        }
    }

    private final EditorView view;
    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final AppPreferences preferences;
    private final Supplier<java.awt.Component> focusOwner;
    private final Consumer<JComponent> focuser;
    private final FindBar bar = new FindBar();
    private final FindHighlightOverlay overlay;
    private final Action findAction;
    private final Action findNextAction;
    private final Action findPreviousAction;
    private final JMenu editMenu;
    private final List<java.awt.Component> menuEntries = new ArrayList<>();
    private final List<Runnable> shortcutRemovers;
    private final Runnable documentListener = () -> search(Reason.EDIT);
    private final Runnable preferenceListener = this::preferencesChanged;
    private Runnable removeBar;
    private List<SearchHit> hits = List.of();
    private int currentIndex = -1;
    /** The current match when the bar closed: reopening goes on from it (like a browser's find bar). */
    private Optional<HitKey> matchWhenClosed = Optional.empty();
    private boolean matchCase;
    private boolean regex;

    public FindFeatures(EditorView view) {
        this(view, () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                component -> component.requestFocusInWindow());
    }

    /** {@code focusOwner} tells where the keyboard focus is; {@code focuser} gives it to a component (tests fake both). */
    FindFeatures(EditorView view, Supplier<java.awt.Component> focusOwner, Consumer<JComponent> focuser) {
        this.view = view;
        this.openDocument = view.openDocument();
        this.canvas = view.canvas();
        this.preferences = view.services().preferences();
        this.focusOwner = focusOwner;
        this.focuser = focuser;
        this.matchCase = preferences.findMatchCase();
        this.regex = preferences.findRegex();
        this.overlay = new FindHighlightOverlay(canvas::scheme, openDocument.registry(), canvas::repaint);
        this.findAction = action("action.find", this::find);
        this.findNextAction = action("action.findNext", this::next);
        this.findPreviousAction = action("action.findPrevious", this::previous);
        Icons.install(findAction, FindIcons.FIND);
        this.editMenu = view.menu(MenuId.EDIT);
        addMenuEntries();
        this.shortcutRemovers = List.of(view.registerShortcutAction("edit.find", findAction),
                view.registerShortcutAction("edit.findNext", findNextAction),
                view.registerShortcutAction("edit.findPrevious", findPreviousAction));
        wireBar();
        canvas.addOverlay(overlay);
        preferences.addListener(preferenceListener);
        syncToggles();
    }

    public Action findAction() {
        return findAction;
    }

    public Action findNextAction() {
        return findNextAction;
    }

    public Action findPreviousAction() {
        return findPreviousAction;
    }

    FindBar bar() {
        return bar;
    }

    FindHighlightOverlay overlay() {
        return overlay;
    }

    public boolean isOpen() {
        return removeBar != null;
    }

    /** The matches of the open bar in order; empty while it is closed. */
    public List<SearchHit> hits() {
        return hits;
    }

    public Optional<SearchHit> current() {
        return currentIndex >= 0 && currentIndex < hits.size() ? Optional.of(hits.get(currentIndex))
                : Optional.empty();
    }

    /** Takes back the bar, the overlay, the menu items, the shortcut actions and every listener. */
    public void dispose() {
        closeBar();
        canvas.removeOverlay(overlay);
        preferences.removeListener(preferenceListener);
        shortcutRemovers.forEach(Runnable::run);
        menuEntries.forEach(editMenu::remove);
        menuEntries.clear();
    }

    /** Ctrl+F: a component's own filter when the focus is in it, else the bar, with the whole text selected. */
    private void find() {
        Optional<Runnable> ownFilter = ownFilterOf(focusOwner.get());
        if (ownFilter.isPresent()) {
            ownFilter.get().run();
            return;
        }
        if (!isOpen()) {
            openBar(Reason.QUERY);
        }
        focuser.accept(bar.field());
        bar.field().selectAll();
    }

    private void next() {
        step(1);
    }

    private void previous() {
        step(-1);
    }

    /**
     * Goes {@code direction} matches further, wrapping around. With the bar closed it opens it first (F3): it goes on
     * from the match the user was on when the bar closed; when that one no longer matches, F3 lands on the first match
     * and Mayús+F3 on the last.
     */
    private void step(int direction) {
        if (!isOpen()) {
            openBar(Reason.QUIET_OPEN);
            focuser.accept(bar.field());
        }
        if (hits.isEmpty()) {
            return;
        }
        currentIndex = currentIndex < 0 ? (direction > 0 ? 0 : hits.size() - 1)
                : Math.floorMod(currentIndex + direction, hits.size());
        showResults();
        goToCurrent();
    }

    private void openBar(Reason reason) {
        removeBar = view.addAboveCanvas(bar);
        openDocument.addChangeListener(documentListener);
        Optional<HitKey> remembered = matchWhenClosed;
        matchWhenClosed = Optional.empty();
        search(reason, remembered, true);
    }

    /** Esc and ✕: closes the bar and gives the focus back to the canvas. */
    private void close() {
        if (isOpen()) {
            closeBar();
            focuser.accept(canvas);
        }
    }

    private void closeBar() {
        if (removeBar == null) {
            return;
        }
        removeBar.run();
        removeBar = null;
        openDocument.removeChangeListener(documentListener);
        matchWhenClosed = current().map(HitKey::of);
        hits = List.of();
        currentIndex = -1;
        overlay.clear();
    }

    /** Searches the document again and shows the matches; see the class comment for which one becomes current. */
    private void search(Reason reason) {
        search(reason, current().map(HitKey::of), false);
    }

    /**
     * {@code previousKey}: the match that stays current if it still matches. {@code opening}: the bar has just opened
     * (Ctrl+F travels to the current match even when it is the one of before; F3 leaves the choice to its step).
     */
    private void search(Reason reason, Optional<HitKey> previousKey, boolean opening) {
        if (!isOpen()) {
            return;
        }
        String text = bar.field().getText();
        CompiledQuery query = new SearchQuery(text, matchCase, regex).compile();
        ComponentTypeRegistry registry = openDocument.registry();
        List<SearchHit> found = CircuitSearch.find(openDocument.document(), registry, query, TypeNames.in(registry));
        Optional<SearchProblem> problem = query.problem(); // after the search: a regex may give up while searching
        int previousIndex = currentIndex;
        hits = found;
        int index = -1;
        if (previousKey.isPresent()) {
            for (int candidate = 0; candidate < hits.size(); candidate++) {
                if (HitKey.of(hits.get(candidate)).equals(previousKey.get())) {
                    index = candidate;
                    break;
                }
            }
        }
        if (index < 0 && !hits.isEmpty() && reason != Reason.QUIET_OPEN) {
            index = reason == Reason.EDIT && previousIndex >= 0 && previousIndex < hits.size() ? previousIndex : 0;
        }
        currentIndex = hits.isEmpty() ? -1 : index; // QUIET_OPEN without the match of before: none yet, F3 picks
        showResults(text, problem);
        boolean currentChanged = !Objects.equals(previousKey, current().map(HitKey::of));
        if (reason == Reason.QUERY && (currentChanged || opening)) {
            goToCurrent();
        }
    }

    private void showResults() {
        showResults(bar.field().getText(), Optional.empty());
    }

    private void showResults(String text, Optional<SearchProblem> problem) {
        if (text.isEmpty()) {
            bar.showState("", "", false);
        } else if (problem.isPresent()) {
            bar.showState("", Messages.format(problem.get().messageKey(), problem.get().arguments().toArray()), true);
        } else if (hits.isEmpty()) {
            bar.showState(Messages.text("find.noResults"), "", true);
        } else {
            bar.showState(Messages.format("find.counter", currentIndex + 1, hits.size()),
                    current().map(this::whereText).orElse(""), false);
        }
        Set<ElementId> highlighted = new LinkedHashSet<>();
        hits.forEach(hit -> highlighted.add(hit.elementId()));
        overlay.show(highlighted, current().map(SearchHit::elementId));
    }

    /** «"NOT", dentro de "Inversor" (U3)» for a match inside a user component; empty for a top-level one. */
    private String whereText(SearchHit hit) {
        if (!hit.nested()) {
            return "";
        }
        Optional<Component> instance = openDocument.document().component(hit.elementId());
        if (instance.isEmpty()) {
            return "";
        }
        String path = String.join(" ▸ ", hit.nestedPath());
        String typeName = TypeNames.of(instance.get().typeId(), openDocument.registry());
        String label = instance.get().label();
        return label.isBlank() ? Messages.format("find.inside", path, typeName)
                : Messages.format("find.insideLabeled", path, typeName, label);
    }

    /** Selects the current match's element and takes the view there. */
    private void goToCurrent() {
        current().flatMap(hit -> openDocument.document().element(hit.elementId())).ifPresent(element -> {
            canvas.selection().select(element.id());
            canvas.travelTo(DrawnBounds.of(element, openDocument.registry()));
        });
    }

    private void wireBar() {
        SearchField field = bar.field();
        field.onTextChange(() -> search(Reason.QUERY));
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "find.next", this::next);
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "find.next", this::next);
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "find.previous",
                this::previous);
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "find.previous", this::previous);
        bind(field, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "find.close", this::close);
        bar.nextButton().addActionListener(event -> next());
        bar.previousButton().addActionListener(event -> previous());
        bar.closeButton().addActionListener(event -> close());
        bar.matchCaseToggle().addActionListener(event ->
                preferences.setFindMatchCase(bar.matchCaseToggle().isSelected()));
        bar.regexToggle().addActionListener(event -> preferences.setFindRegex(bar.regexToggle().isSelected()));
        bar.animateToggle().addActionListener(event ->
                preferences.setAnimateViewMoves(bar.animateToggle().isSelected()));
    }

    private static void bind(JComponent component, KeyStroke stroke, String name, Runnable whenPressed) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                whenPressed.run();
            }
        });
    }

    /** The toggles follow the preferences (changed here, in another view or in Preferencias); new options search again. */
    private void preferencesChanged() {
        syncToggles();
        if (preferences.findMatchCase() != matchCase || preferences.findRegex() != regex) {
            matchCase = preferences.findMatchCase();
            regex = preferences.findRegex();
            search(Reason.QUERY);
        }
    }

    private void syncToggles() {
        bar.matchCaseToggle().setSelected(preferences.findMatchCase());
        bar.regexToggle().setSelected(preferences.findRegex());
        bar.animateToggle().setSelected(preferences.animateViewMoves());
    }

    /** «Buscar…», «Buscar siguiente» and «Buscar anterior» after «Seleccionar todo», after a separator. */
    private void addMenuEntries() {
        int index = editMenu.getMenuComponentCount();
        Action selectAll = view.selectionActions().selectAll();
        java.awt.Component[] children = editMenu.getMenuComponents();
        for (int position = 0; position < children.length; position++) {
            if (children[position] instanceof JMenuItem item && item.getAction() == selectAll) {
                index = position + 1;
            }
        }
        menuEntries.add(new JPopupMenu.Separator());
        menuEntries.add(new JMenuItem(findAction));
        menuEntries.add(new JMenuItem(findNextAction));
        menuEntries.add(new JMenuItem(findPreviousAction));
        for (java.awt.Component entry : menuEntries) {
            editMenu.getPopupMenu().insert(entry, index++);
        }
    }

    private static Optional<Runnable> ownFilterOf(java.awt.Component focused) {
        for (java.awt.Component component = focused; component != null; component = component.getParent()) {
            if (component instanceof JComponent swing
                    && swing.getClientProperty(FILTER_FOCUS_PROPERTY) instanceof Runnable focusFilter) {
                return Optional.of(focusFilter);
            }
        }
        return Optional.empty();
    }

    private static Action action(String nameKey, Runnable whenPerformed) {
        return new AbstractAction(Messages.text(nameKey)) {
            @Override
            public void actionPerformed(ActionEvent event) {
                whenPerformed.run();
            }
        };
    }
}
