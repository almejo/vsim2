package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.window.closed.ClosedEntry;
import cl.almejo.vsim.ui.window.closed.ClosedGroup;
import cl.almejo.vsim.ui.window.closed.ClosedTab;
import cl.almejo.vsim.ui.window.closed.ClosedTabState;
import cl.almejo.vsim.ui.window.closed.ClosedTabs;
import cl.almejo.vsim.ui.window.closed.ClosedWindow;
import cl.almejo.vsim.ui.window.closed.Closing;
import cl.almejo.vsim.ui.window.closed.DocumentReference;
import cl.almejo.vsim.ui.window.closed.WindowReference;

import javax.swing.KeyStroke;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Ventana ▸ «Reabrir pestaña cerrada» (Ctrl+Mayús+T, card «ctrl-t»): takes the newest entry of the {@link ClosedTabs}
 * and brings it back, in its window when that is still open, else in the window where it was pressed. A circuit still
 * open somewhere comes back on the open document; a closed one is read again from its file; one never saved, a file
 * that is gone or a component no longer in its circuit cannot come back, and a notice over the circuit says so (the
 * entry is dropped). EDT only.
 */
public final class ClosedTabReopener {

    /** How long the notices that explain a loss stay (changes lost, never saved). */
    public static final Duration LONG_NOTICE = Duration.ofSeconds(8);
    /** How long the other notices stay. */
    public static final Duration SHORT_NOTICE = Duration.ofSeconds(5);

    /** What the reopening needs from the application. */
    public interface Windows {

        /** Whether {@code window} is still an open window of the application. */
        boolean isOpen(WindowTabs window);

        /** The open document of {@code path}, when some window holds it. */
        Optional<OpenDocument> openDocumentOf(Path path);

        /**
         * Reads {@code path} as Abrir does (an unreadable file is explained over {@code target}), ready to be shown and
         * tracked; empty when it could not be read.
         */
        Optional<OpenDocument> read(Path path, WindowTabs target);

        /** A new window with no tab yet, not shown. */
        WindowTabs newWindow();

        /** Shows {@code window}, which has tabs now, placed as {@code bounds} says (clamped to the screens). */
        void show(WindowTabs window, WindowState bounds);

        /** Brings {@code window} to the front. */
        void focus(WindowTabs window);

        /** Takes away a window {@link #newWindow} made that could not be filled: it is never shown. */
        void discard(WindowTabs window);

        /** Closes the untouched «Sin título» of {@code window}, which got something else, as Abrir does. */
        void closeReplaced(WindowTabs window);

        /** Shows {@code text} over the circuit of {@code window}'s active tab for {@code duration}. */
        void notice(WindowTabs window, String text, Duration duration);
    }

    /** The notices of one reopening, shown together once the tabs are in. */
    private static final class Notices {
        private final List<String> texts = new ArrayList<>();
        private Duration duration = SHORT_NOTICE;

        void add(String text, Duration shownFor) {
            texts.add(text);
            if (shownFor.compareTo(duration) > 0) {
                duration = shownFor;
            }
        }

        void showOver(Windows windows, WindowTabs window) {
            if (!texts.isEmpty()) {
                windows.notice(window, String.join(" ", texts), duration);
            }
        }
    }

    private final ClosedTabs closedTabs;
    private final Windows windows;
    private final Clock clock;
    private final Supplier<Optional<KeyStroke>> reopenKey;

    /**
     * @param clock     tells «hoy» from another day in the time a lost-changes notice gives
     * @param reopenKey the key of «Reabrir pestaña cerrada» now, named by the never-saved notice
     */
    public ClosedTabReopener(ClosedTabs closedTabs, Windows windows, Clock clock,
                             Supplier<Optional<KeyStroke>> reopenKey) {
        this.closedTabs = Objects.requireNonNull(closedTabs, "closedTabs");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reopenKey = Objects.requireNonNull(reopenKey, "reopenKey");
    }

    /** Brings back the newest closed entry; {@code requester} is the window where it was asked for. */
    public void reopen(WindowTabs requester) {
        Optional<ClosedEntry> newest = closedTabs.pop();
        if (newest.isEmpty()) {
            windows.notice(requester, Messages.text("reopen.empty"), SHORT_NOTICE);
            return;
        }
        switch (newest.get()) {
            case ClosedTab tab -> reopenTab(tab, requester);
            case ClosedGroup group -> reopenGroup(group, requester);
            case ClosedWindow window -> reopenWindow(window, requester);
        }
    }

    private void reopenTab(ClosedTab closed, WindowTabs requester) {
        Notices notices = new Notices();
        Optional<WindowTabs> original = openWindow(closed.window());
        Optional<OpenDocument> open = closed.document().openDocument();
        WindowTabs target = open
                .flatMap(document -> Stream.concat(original.stream(), Stream.of(requester))
                        .filter(window -> window.documents().contains(document)).findFirst())
                .orElseGet(() -> original.orElse(requester));
        Optional<OpenDocument> document = open.isPresent() ? open : resolve(closed.document(), target, notices);
        if (document.isEmpty()) {
            notices.showOver(windows, requester);
            return;
        }
        ClosedTabState tab = closed.tab();
        boolean definitionGone = tab.definitionId()
                .filter(definitionId -> document.get().document().definition(definitionId).isEmpty()).isPresent();
        if (definitionGone) {
            target.showPlace(document.get(), Optional.empty(), Optional.empty());
            notices.add(componentGone(tab, document.get()), SHORT_NOTICE);
        } else {
            target.reopenTab(document.get(), tab, closed.indexInGroup());
        }
        finish(target, requester, notices);
    }

    private void reopenGroup(ClosedGroup closed, WindowTabs requester) {
        Notices notices = new Notices();
        WindowTabs target = openWindow(closed.window()).orElse(requester);
        Optional<OpenDocument> document = closed.document().openDocument()
                .or(() -> resolve(closed.document(), target, notices));
        if (document.isEmpty()) {
            notices.showOver(windows, requester);
            return;
        }
        restoreInto(target, document.get(), closed, closed.groupIndex(), notices);
        finish(target, requester, notices);
    }

    private void reopenWindow(ClosedWindow closed, WindowTabs requester) {
        Notices notices = new Notices();
        List<ClosedGroup> groups = new ArrayList<>();
        List<OpenDocument> documents = new ArrayList<>();
        for (ClosedGroup group : closed.groups()) {
            Optional<OpenDocument> document = group.document().openDocument()
                    .or(() -> resolve(group.document(), requester, notices));
            document.ifPresent(found -> {
                groups.add(group);
                documents.add(found);
            });
        }
        if (groups.isEmpty()) {
            notices.showOver(windows, requester);
            return;
        }
        ClosedGroup activeGroup = closed.groups().get(Math.min(Math.max(0, closed.activeGroup()),
                closed.groups().size() - 1));
        WindowTabs created = windows.newWindow();
        EditorView active = null;
        try {
            for (int index = 0; index < groups.size(); index++) {
                EditorView restored = restoreInto(created, documents.get(index), groups.get(index), Integer.MAX_VALUE,
                        notices);
                if (groups.get(index) == activeGroup) {
                    active = restored;
                }
            }
        } catch (RuntimeException exception) {
            // no half-built window stays registered, and the entry can be tried again
            windows.discard(created);
            closedTabs.push(closed);
            throw exception;
        }
        if (active != null) {
            created.activate(active);
        }
        windows.show(created, withActiveTabView(closed.bounds(), groups.contains(activeGroup) ? activeGroup
                : groups.getFirst()));
        notices.showOver(windows, created);
    }

    private EditorView restoreInto(WindowTabs target, OpenDocument document, ClosedGroup closed, int groupIndex,
                                   Notices notices) {
        WindowTabs.RestoredGroup restored = target.restoreGroup(document, closed.tabs(), closed.activeTab(),
                closed.folded(), groupIndex);
        restored.skipped().forEach(tab -> notices.add(componentGone(tab, document), SHORT_NOTICE));
        return restored.active();
    }

    /**
     * Where the window was, looking where its active tab looked: the bounds keep the view of the tab active when it
     * closed, which may be a group that did not come back, and placing the window must not overwrite the tab's own view.
     */
    private static WindowState withActiveTabView(WindowState bounds, ClosedGroup active) {
        List<ClosedTabState> tabs = active.tabs();
        if (tabs.isEmpty()) {
            return bounds;
        }
        ClosedTabState tab = tabs.get(Math.min(Math.max(0, active.activeTab()), tabs.size() - 1));
        return WindowState.of(bounds.bounds(), bounds.maximized(), tab.view());
    }

    private void finish(WindowTabs target, WindowTabs requester, Notices notices) {
        windows.closeReplaced(target);
        if (target != requester) {
            windows.focus(target);
        }
        notices.showOver(windows, target);
    }

    /** The window of {@code reference} while it is still open. */
    private Optional<WindowTabs> openWindow(WindowReference reference) {
        return reference.window()
                .filter(WindowTabs.class::isInstance).map(WindowTabs.class::cast)
                .filter(window -> !window.isDisposed() && windows.isOpen(window));
    }

    /**
     * The document of a circuit that closed: the one open now for its file, else its file read again; empty, with a
     * notice, when it was never saved or its file is gone (an unreadable file is explained by {@link Windows#read}).
     */
    private Optional<OpenDocument> resolve(DocumentReference reference, WindowTabs target, Notices notices) {
        Optional<Path> path = reference.path();
        if (path.isEmpty()) {
            notices.add(Messages.format("reopen.neverSaved", reference.displayName(), reopenKeyName()), LONG_NOTICE);
            return Optional.empty();
        }
        Optional<OpenDocument> open = windows.openDocumentOf(path.get());
        if (open.isPresent()) {
            return open;
        }
        if (!Files.isRegularFile(path.get())) {
            notices.add(Messages.format("reopen.fileMissing", fileName(path.get()), folderOf(path.get())),
                    SHORT_NOTICE);
            return Optional.empty();
        }
        Optional<OpenDocument> read = windows.read(path.get(), target);
        if (read.isPresent() && reference.closing() == Closing.LOST_CHANGES) {
            notices.add(lostChanges(reference.displayName(), path.get()), LONG_NOTICE);
        }
        return read;
    }

    private String reopenKeyName() {
        return reopenKey.get().map(KeyNames::describe).orElseGet(() -> Messages.text("action.reopenClosedTab"));
    }

    /** «contador volvió como estaba guardado (hoy, 14:32)…», with the time the file was last written. */
    private String lostChanges(String name, Path path) {
        try {
            ZonedDateTime saved = Files.getLastModifiedTime(path).toInstant().atZone(clock.getZone());
            String time = DateTimeFormatter.ofPattern(Messages.text("reopen.time.pattern"), Messages.locale())
                    .format(saved);
            String when = saved.toLocalDate().equals(LocalDate.now(clock))
                    ? Messages.format("reopen.savedToday", time)
                    : Messages.format("reopen.savedOn", DateTimeFormatter
                    .ofPattern(Messages.text("reopen.date.pattern"), Messages.locale()).format(saved), time);
            return Messages.format("reopen.lostChanges", name, when);
        } catch (IOException exception) {
            return Messages.format("reopen.lostChanges.noTime", name);
        }
    }

    private static String componentGone(ClosedTabState tab, OpenDocument document) {
        return Messages.format("reopen.componentGone",
                tab.definitionName().orElseGet(() -> tab.definitionId().orElse("")), document.displayName());
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private static String folderOf(Path path) {
        Path folder = path.toAbsolutePath().getParent();
        return folder == null ? "" : folder.toString();
    }
}
