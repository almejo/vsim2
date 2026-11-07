package cl.almejo.vsim.ui.window.tabs;

import cl.almejo.vsim.ui.app.Listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The tabs of one window as groups (spec §4.2–4.3), without Swing: one group per open document, its main tabs first
 * and then its component tabs, groups in order of arrival. A group is a unit: it moves, folds and closes as one, and a
 * component tab never lives outside its document's group. A folded group shows only its main tabs. Exactly one tab is
 * active while there is any. Listeners hear every change. EDT only.
 */
public final class TabStripModel {

    /**
     * One tab: a main tab of {@code document} when {@code definitionId} is empty, else a component tab of that
     * definition. {@code document} is the group's identity (the open document in the window, any value in tests).
     */
    public record Tab(String id, Object document, Optional<String> definitionId, String title, boolean modified) {

        public Tab {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(title, "title");
        }

        public boolean isMain() {
            return definitionId.isEmpty();
        }
    }

    /** All tabs of one document in this window, main tabs first; {@code folded} hides its component tabs. */
    public record Group(Object document, List<Tab> tabs, boolean folded) {

        public Group {
            Objects.requireNonNull(document, "document");
            tabs = List.copyOf(tabs);
        }

        public List<Tab> mainTabs() {
            return tabs.stream().filter(Tab::isMain).toList();
        }

        public List<Tab> componentTabs() {
            return tabs.stream().filter(tab -> !tab.isMain()).toList();
        }

        /** What the strip shows of this group: everything, or only the main tabs while folded. */
        public List<Tab> visibleTabs() {
            return folded ? mainTabs() : tabs;
        }
    }

    /** A group while it lives in the model: its tabs change in place. */
    private static final class Entry {
        private final Object document;
        private final List<Tab> tabs = new ArrayList<>();
        private boolean folded;

        Entry(Object document) {
            this.document = document;
        }

        Group snapshot() {
            return new Group(document, tabs, folded);
        }

        int mainTabCount() {
            return (int) tabs.stream().filter(Tab::isMain).count();
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Listeners<Runnable> listeners = new Listeners<>();
    private String activeId;

    public List<Group> groups() {
        return entries.stream().map(Entry::snapshot).toList();
    }

    public Optional<Group> group(Object document) {
        return entryOf(document).map(Entry::snapshot);
    }

    /** The tabs the strip shows, in order: folded groups show their main tabs only. */
    public List<Tab> visibleTabs() {
        return entries.stream().flatMap(entry -> entry.snapshot().visibleTabs().stream()).toList();
    }

    /** Every tab, hidden ones included, in order. */
    public List<Tab> tabs() {
        return entries.stream().flatMap(entry -> entry.tabs.stream()).toList();
    }

    public Optional<Tab> active() {
        return activeId == null ? Optional.empty() : tab(activeId);
    }

    public Optional<Tab> tab(String id) {
        return tabs().stream().filter(tab -> tab.id().equals(id)).findFirst();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Makes {@code tabId} the active tab; a hidden component tab unfolds its group first (entering unfolds). */
    public void activate(String tabId) {
        Entry entry = entryOfTab(tabId).orElseThrow(() -> new IllegalArgumentException("unknown tab " + tabId));
        if (tabId.equals(activeId) && !entry.folded) {
            return;
        }
        Tab tab = tab(tabId).orElseThrow();
        if (!tab.isMain()) {
            entry.folded = false;
        }
        activeId = tabId;
        changed();
    }

    /**
     * Adds {@code tab}: a main tab goes after the last main tab of its group, a component tab at the end of its group;
     * a new document's group goes last. A component tab needs its group to exist. The first tab becomes active.
     */
    public void add(Tab tab) {
        if (tab(tab.id()).isPresent()) {
            throw new IllegalArgumentException("tab already in the strip: " + tab.id());
        }
        Optional<Entry> existing = entryOf(tab.document());
        if (existing.isEmpty() && !tab.isMain()) {
            throw new IllegalArgumentException("a component tab needs its circuit's group: " + tab.id());
        }
        Entry entry = existing.orElseGet(() -> {
            Entry created = new Entry(tab.document());
            entries.add(created);
            return created;
        });
        entry.tabs.add(tab.isMain() ? entry.mainTabCount() : entry.tabs.size(), tab);
        if (activeId == null) {
            activeId = tab.id();
        }
        changed();
    }

    /** Replaces the tab with the same id (its title or modified mark changed); nothing happens for an unknown id. */
    public void update(Tab tab) {
        for (Entry entry : entries) {
            for (int index = 0; index < entry.tabs.size(); index++) {
                Tab current = entry.tabs.get(index);
                if (current.id().equals(tab.id())) {
                    if (!current.equals(tab)) {
                        entry.tabs.set(index, tab);
                        changed();
                    }
                    return;
                }
            }
        }
    }

    /**
     * Removes {@code tabId}. Removing the last main tab of a group removes the whole group (its component tabs go with
     * it). When the active tab goes, the visible tab after it (else before it) becomes active.
     */
    public void remove(String tabId) {
        Entry entry = entryOfTab(tabId).orElseThrow(() -> new IllegalArgumentException("unknown tab " + tabId));
        Tab tab = tab(tabId).orElseThrow();
        List<Tab> visibleBefore = visibleTabs();
        if (tab.isMain() && entry.mainTabCount() == 1) {
            entries.remove(entry);
        } else {
            entry.tabs.remove(tab);
        }
        reactivateAfterRemoval(visibleBefore);
        changed();
    }

    /** Moves the group of {@code document} so that it ends at {@code toGroupIndex} (clamped). */
    public void moveGroup(Object document, int toGroupIndex) {
        Entry entry = entryOf(document).orElseThrow(() -> new IllegalArgumentException("no group for " + document));
        entries.remove(entry);
        entries.add(clamp(toGroupIndex, entries.size()), entry);
        changed();
    }

    /** Removes the group of {@code document} and hands it back (to put into another window). */
    public Group takeGroup(Object document) {
        Entry entry = entryOf(document).orElseThrow(() -> new IllegalArgumentException("no group for " + document));
        List<Tab> visibleBefore = visibleTabs();
        entries.remove(entry);
        reactivateAfterRemoval(visibleBefore);
        changed();
        return entry.snapshot();
    }

    /**
     * Puts {@code group} at {@code atIndex} (clamped). When this model already has a group of that document, the
     * arriving component tabs are appended to it and the arriving main tabs are dropped (spec §4.3): the window then
     * holds one group per document. With no active tab, the group's first tab becomes active.
     */
    public void putGroup(Group group, int atIndex) {
        Optional<Entry> existing = entryOf(group.document());
        if (existing.isPresent()) {
            existing.get().tabs.addAll(group.componentTabs());
        } else {
            Entry entry = new Entry(group.document());
            entry.tabs.addAll(group.tabs());
            entry.folded = group.folded();
            entries.add(clamp(atIndex, entries.size()), entry);
        }
        if (activeId == null && !group.tabs().isEmpty()) {
            activeId = existing.isPresent() ? existing.get().tabs.get(0).id() : group.tabs().get(0).id();
        }
        changed();
    }

    /** Folds or unfolds the group; folding while one of its component tabs is active activates its main tab. */
    public void fold(Object document, boolean folded) {
        Entry entry = entryOf(document).orElseThrow(() -> new IllegalArgumentException("no group for " + document));
        if (entry.folded == folded) {
            return;
        }
        entry.folded = folded;
        if (folded && active().filter(tab -> !tab.isMain() && tab.document().equals(document)).isPresent()) {
            activeId = entry.tabs.get(0).id();
        }
        changed();
    }

    public boolean isFolded(Object document) {
        return entryOf(document).map(entry -> entry.folded).orElse(false);
    }

    /** Activates the visible tab after the active one, wrapping (Ctrl+Tab). */
    public void next() {
        step(1);
    }

    /** Activates the visible tab before the active one, wrapping (Ctrl+Mayús+Tab). */
    public void previous() {
        step(-1);
    }

    public int componentTabCount(Object document) {
        return entryOf(document).map(entry -> entry.tabs.size() - entry.mainTabCount()).orElse(0);
    }

    /** Runs {@code listener} after every change; returns what removes it. */
    public Runnable addListener(Runnable listener) {
        return listeners.add(listener);
    }

    private void step(int direction) {
        List<Tab> visible = visibleTabs();
        if (visible.isEmpty()) {
            return;
        }
        int current = indexOf(visible, activeId);
        int next = Math.floorMod((current < 0 ? 0 : current + direction), visible.size());
        activate(visible.get(next).id());
    }

    private void reactivateAfterRemoval(List<Tab> visibleBefore) {
        if (activeId != null && tab(activeId).isPresent()) {
            return;
        }
        List<Tab> visibleNow = visibleTabs();
        if (visibleNow.isEmpty()) {
            activeId = null;
            return;
        }
        int removedAt = indexOf(visibleBefore, activeId);
        for (int index = removedAt + 1; index < visibleBefore.size(); index++) {
            String candidate = visibleBefore.get(index).id();
            if (indexOf(visibleNow, candidate) >= 0) {
                activeId = candidate;
                return;
            }
        }
        for (int index = removedAt - 1; index >= 0; index--) {
            String candidate = visibleBefore.get(index).id();
            if (indexOf(visibleNow, candidate) >= 0) {
                activeId = candidate;
                return;
            }
        }
        activeId = visibleNow.get(0).id();
    }

    private static int indexOf(List<Tab> tabs, String id) {
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private Optional<Entry> entryOf(Object document) {
        return entries.stream().filter(entry -> entry.document.equals(document)).findFirst();
    }

    private Optional<Entry> entryOfTab(String tabId) {
        return entries.stream()
                .filter(entry -> entry.tabs.stream().anyMatch(tab -> tab.id().equals(tabId)))
                .findFirst();
    }

    private static int clamp(int index, int size) {
        return Math.max(0, Math.min(index, size));
    }

    private void changed() {
        listeners.fire(Runnable::run);
    }
}
