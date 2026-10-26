package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The «N ajustes cambiados» section of the AI panel and of the Presentar panel: a toggle header with the count and,
 * folded until the header is pressed, one line per change. Each panel gives its own layout gaps and insets and the
 * label to draw a line with; {@link #show(List)} fills it and hides the section when there is nothing to list. The
 * panel may add more rows below (the AI panel's «Conservar» and «Descartar»). EDT only.
 */
public final class ChangedSettingsSection extends JPanel {

    private final JToggleButton header = new JToggleButton();
    private final JPanel list;
    private final String itemConstraints;
    private final Function<String, JLabel> labelFactory;
    private final List<JLabel> labels = new ArrayList<>();

    /**
     * @param sectionLayout   the MigLayout constraints of the section
     * @param listLayout      the MigLayout constraints of the list (its insets and gaps)
     * @param shrinkable      an extra constraint for the header, the list and every line ({@code "wmin 0"} to let
     *                        them shrink below their text, or empty)
     * @param labelFactory    builds the label of one change from its text
     * @param onFoldToggled   runs after the header shows or folds the list
     */
    public ChangedSettingsSection(String sectionLayout, String listLayout, String shrinkable,
                                  Function<String, JLabel> labelFactory, Runnable onFoldToggled) {
        super(new MigLayout(sectionLayout, "[grow, fill]"));
        this.labelFactory = labelFactory;
        this.itemConstraints = shrinkable;
        this.list = new JPanel(new MigLayout(listLayout));
        list.setVisible(false);
        header.addActionListener(event -> {
            list.setVisible(header.isSelected());
            onFoldToggled.run();
        });
        String extra = shrinkable.isEmpty() ? "" : ", " + shrinkable;
        add(header, "growx 0" + extra);
        add(list, "hidemode 3" + extra);
    }

    /** Lists {@code changes}; the whole section is hidden when there are none. */
    public void showChanges(List<String> changes) {
        setVisible(!changes.isEmpty());
        header.setText(Messages.format("ai.panel.settings", Integer.toString(changes.size())));
        list.removeAll();
        labels.clear();
        for (String change : changes) {
            JLabel label = labelFactory.apply(change);
            labels.add(label);
            list.add(label, itemConstraints);
        }
    }

    /** Makes the section and its list see-through (the floating panel paints its own background). */
    public void setTransparent() {
        setOpaque(false);
        list.setOpaque(false);
    }

    public JToggleButton header() {
        return header;
    }

    public JPanel changesList() {
        return list;
    }

    public List<String> texts() {
        return labels.stream().map(JLabel::getText).toList();
    }
}
