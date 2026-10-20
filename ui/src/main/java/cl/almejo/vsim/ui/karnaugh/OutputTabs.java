package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JTabbedPane;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One tab per output with its Karnaugh map (steps 2 and 3). The selected tab follows the model's selected output both
 * ways, so steps 2 and 3 show the same output; the tabs are rebuilt when the outputs change. A refresh also tells
 * the maps that the practice may have changed (their mode, gestures and ants). In step 3 of the practice wizard each
 * tab marks where its map (in the chosen form) stands: «F ✓» done, «F ✗» given up, «F •» pending, with a tooltip.
 */
final class OutputTabs extends JTabbedPane {

    private final KarnaughModel model;
    private final HoverState hover;
    private final boolean showGroups;
    private final List<KarnaughMapView> views = new ArrayList<>();
    private boolean updating;

    OutputTabs(KarnaughModel model, HoverState hover, boolean showGroups) {
        this.model = model;
        this.hover = hover;
        this.showGroups = showGroups;
        rebuild();
        addChangeListener(event -> {
            if (!updating && getSelectedIndex() >= 0) {
                model.setSelectedOutput(getSelectedIndex());
            }
        });
        hover.addListener(this::repaintViews);
    }

    KarnaughMapView view(int output) {
        return views.get(output);
    }

    /** Matches the tabs to the model: their number, titles, selection; repaints the maps. */
    void refresh() {
        updating = true;
        try {
            if (views.size() != model.outputCount()) {
                rebuild();
            }
            for (int output = 0; output < model.outputCount(); output++) {
                String title = title(output);
                if (!title.equals(getTitleAt(output))) {
                    setTitleAt(output, title);
                }
                String tooltip = tooltip(output);
                if (!Objects.equals(tooltip, getToolTipTextAt(output))) {
                    setToolTipTextAt(output, tooltip);
                }
            }
            if (getSelectedIndex() != model.selectedOutput()) {
                setSelectedIndex(model.selectedOutput());
            }
        } finally {
            updating = false;
        }
        views.forEach(KarnaughMapView::refreshPractice);
    }

    /** Whether the tabs say where each output's practice stands (step 3 of the practice wizard). */
    private boolean marksPractice() {
        return showGroups && model.practice().practising() && model.problem().isEmpty();
    }

    /** The output's name, with «✓», «✗» or «•» in practice (done, given up, pending). */
    private String title(int output) {
        String name = model.outputNames().get(output);
        if (!marksPractice()) {
            return name;
        }
        return Messages.format(stateKey(output) + ".tab", name);
    }

    /** What the mark means («F: lista, tus grupos cubren la tabla»…), or none outside the practice. */
    private String tooltip(int output) {
        if (!marksPractice()) {
            return null;
        }
        return Messages.format(stateKey(output) + ".tooltip", model.outputNames().get(output).strip());
    }

    private String stateKey(int output) {
        return switch (model.practice().state(output, model.options().form())) {
            case DONE -> "karnaugh.practice.state.done";
            case GIVEN_UP -> "karnaugh.practice.state.givenUp";
            case PENDING -> "karnaugh.practice.state.pending";
        };
    }

    private void rebuild() {
        boolean wasUpdating = updating;
        updating = true;
        try {
            removeAll();
            views.clear();
            for (int output = 0; output < model.outputCount(); output++) {
                KarnaughMapView view = new KarnaughMapView(model, hover, output, showGroups);
                views.add(view);
                addTab(model.outputNames().get(output), view);
            }
            setSelectedIndex(Math.min(model.selectedOutput(), views.size() - 1));
        } finally {
            updating = wasUpdating;
        }
    }

    private void repaintViews() {
        views.forEach(KarnaughMapView::repaint);
    }
}
