package cl.almejo.vsim.ui.karnaugh;

import javax.swing.JTabbedPane;
import java.util.ArrayList;
import java.util.List;

/**
 * One tab per output with its Karnaugh map (steps 2 and 3). The selected tab follows the model's selected output both
 * ways, so steps 2 and 3 show the same output; the tabs are rebuilt when the outputs change.
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
            List<String> names = model.outputNames();
            for (int output = 0; output < names.size(); output++) {
                if (!names.get(output).equals(getTitleAt(output))) {
                    setTitleAt(output, names.get(output));
                }
            }
            if (getSelectedIndex() != model.selectedOutput()) {
                setSelectedIndex(model.selectedOutput());
            }
        } finally {
            updating = false;
        }
        repaintViews();
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
