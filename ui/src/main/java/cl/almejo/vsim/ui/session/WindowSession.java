package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.window.InspectorState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One circuit window of the last session (spec §4.7, §10.6): where it was ({@code bounds}, whose view is the active
 * tab's), its tabs in the strip's order, which one was active (out of range: the first) and the documents whose
 * groups were folded, and the panels of its left column folded to their header ({@code foldedPanels}: names such as
 * {@code circuit} or {@code palette}, spec §5.3; none means both open) and its right panel ({@code inspector}: open,
 * section and width; empty in a session written before it, which opens as {@link InspectorState#initial()}). Files are compared in any spelling
 * ({@link FileNames#normalized}).
 */
public record WindowSession(WindowState bounds, List<TabState> tabs, int activeTab, List<Path> foldedDocuments,
                            Set<String> foldedPanels, Optional<InspectorState> inspector) {

    public WindowSession {
        Objects.requireNonNull(bounds, "bounds");
        tabs = List.copyOf(tabs);
        foldedDocuments = List.copyOf(foldedDocuments);
        foldedPanels = Set.copyOf(foldedPanels);
        Objects.requireNonNull(inspector, "inspector");
        if (tabs.isEmpty()) {
            throw new IllegalArgumentException("a window of the session has at least one tab");
        }
        if (activeTab < 0 || activeTab >= tabs.size()) {
            activeTab = 0;
        }
    }

    /** A window whose right panel is the initial one (a session written before it, too). */
    public WindowSession(WindowState bounds, List<TabState> tabs, int activeTab, List<Path> foldedDocuments,
                         Set<String> foldedPanels) {
        this(bounds, tabs, activeTab, foldedDocuments, foldedPanels, Optional.empty());
    }

    /** A window whose left column has both panels open (a session written before the circuit tree, too). */
    public WindowSession(WindowState bounds, List<TabState> tabs, int activeTab, List<Path> foldedDocuments) {
        this(bounds, tabs, activeTab, foldedDocuments, Set.of());
    }

    /** The files of the tabs, each once, in the order of their first tab. */
    public List<Path> documents() {
        Set<Path> seen = new HashSet<>();
        List<Path> documents = new ArrayList<>();
        for (TabState tab : tabs) {
            if (seen.add(FileNames.normalized(tab.path()))) {
                documents.add(tab.path());
            }
        }
        return documents;
    }

    /** This window with only the tabs whose file {@code kept} accepts; empty when none is left. */
    public Optional<WindowSession> keeping(Predicate<Path> kept) {
        List<TabState> keptTabs = new ArrayList<>();
        int keptActive = 0;
        for (int index = 0; index < tabs.size(); index++) {
            TabState tab = tabs.get(index);
            if (kept.test(tab.path())) {
                if (index == activeTab) {
                    keptActive = keptTabs.size();
                }
                keptTabs.add(tab);
            }
        }
        if (keptTabs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new WindowSession(bounds, keptTabs, keptActive,
                foldedDocuments.stream().filter(kept).toList(), foldedPanels, inspector));
    }
}
