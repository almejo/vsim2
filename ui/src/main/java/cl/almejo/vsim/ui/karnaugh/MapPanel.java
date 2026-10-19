package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.util.OptionalInt;

/**
 * Step 2 «Mapa» (spec §3): the table on the left and, on the right, the Karnaugh map of each output in its tab, with
 * the note about the Gray order. Hovering a table row lights its map cell and the other way round.
 */
final class MapPanel extends JPanel implements WizardStep {

    private final KarnaughModel model;
    private final HoverState hover;
    private final WizardTable table;
    private final OutputTabs tabs;

    MapPanel(KarnaughModel model, HoverState hover) {
        super(new MigLayout("insets 12, fill", "[pref!][grow, fill]", "[grow, fill]"));
        this.model = model;
        this.hover = hover;
        this.table = new WizardTable(model, hover, false);
        this.tabs = new OutputTabs(model, hover, false);
        JPanel right = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow, fill]", "[][grow, fill][]"));
        right.add(new Paragraph(Messages.text("karnaugh.map.help")), "wmin 100");
        right.add(tabs);
        right.add(new Paragraph(Messages.text("karnaugh.map.note")), "wmin 100");
        add(new JScrollPane(table), "growy");
        add(right);
    }

    WizardTable table() {
        return table;
    }

    KarnaughMapView mapView(int output) {
        return tabs.view(output);
    }

    /** The table row lit in the table (the one under the mouse in the table or on the map). */
    OptionalInt highlightedTableRow() {
        return hover.row() >= 0 && hover.row() < model.rowCount() ? OptionalInt.of(hover.row()) : OptionalInt.empty();
    }

    @Override
    public void refresh() {
        tabs.refresh();
    }
}
