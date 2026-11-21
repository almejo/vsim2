package cl.almejo.vsim.ui.window.closed;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.Listeners;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The tabs the user closed, newest first (card «ctrl-t»): one per application, {@value #CAPACITY} entries at most
 * (the oldest goes), one entry per close (a tab, a whole group or a whole window). An untouched untitled circuit never
 * enters. Quitting VSim keeps nothing: the next start begins empty. EDT only.
 */
public final class ClosedTabs {

    public static final int CAPACITY = 20;

    private final Deque<ClosedEntry> entries = new ArrayDeque<>();
    private final Listeners<Runnable> listeners = new Listeners<>();

    /** Records a close; a group of an untouched untitled circuit (or a window of only those) is left out. */
    public void push(ClosedEntry entry) {
        Optional<ClosedEntry> kept = worthKeeping(entry);
        if (kept.isEmpty()) {
            return;
        }
        entries.addFirst(kept.get());
        while (entries.size() > CAPACITY) {
            entries.removeLast();
        }
        changed();
    }

    private static Optional<ClosedEntry> worthKeeping(ClosedEntry entry) {
        return switch (entry) {
            case ClosedTab tab -> Optional.of(tab);
            case ClosedGroup group -> group.document().isUntouchedUntitled() ? Optional.empty() : Optional.of(group);
            case ClosedWindow window -> {
                List<ClosedGroup> groups = window.groups().stream()
                        .filter(group -> !group.document().isUntouchedUntitled()).toList();
                if (groups.isEmpty()) {
                    yield Optional.empty();
                }
                ClosedGroup active = window.groups().get(Math.min(Math.max(0, window.activeGroup()),
                        window.groups().size() - 1));
                yield Optional.of(new ClosedWindow(groups, Math.max(0, groups.indexOf(active)), window.bounds()));
            }
        };
    }

    /** Takes the newest entry out. */
    public Optional<ClosedEntry> pop() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        ClosedEntry newest = entries.removeFirst();
        changed();
        return Optional.of(newest);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Every entry, newest first. */
    public List<ClosedEntry> entries() {
        return List.copyOf(entries);
    }

    /** {@code document} closed: every entry of it keeps only its file and how it closed. */
    public void documentClosed(OpenDocument document) {
        entries.forEach(entry -> entry.documents().forEach(reference -> reference.documentClosed(document)));
    }

    /** Runs {@code listener} after every push and pop; returns what removes it. */
    public Runnable addListener(Runnable listener) {
        return listeners.add(listener);
    }

    private void changed() {
        listeners.fire(Runnable::run);
    }
}
