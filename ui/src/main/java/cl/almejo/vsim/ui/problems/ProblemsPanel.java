package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Problemas" tab: short circuits and width mismatches of the displayed snapshot (double click navigates), then the
 * component library files that could not be loaded.
 */
public final class ProblemsPanel extends JPanel {

    private final Consumer<Problem> navigate;
    private final JLabel summary = new JLabel();
    private final DefaultListModel<Problem> model = new DefaultListModel<>();
    private final JList<Problem> list = new JList<>(model);
    private final JLabel libraryHeader = new JLabel();
    private final DefaultListModel<String> libraryModel = new DefaultListModel<>();
    private final JScrollPane libraryScroll = new JScrollPane(new JList<>(libraryModel));
    private List<Problem> shown;
    private int refreshCount;

    public ProblemsPanel(OpenDocument openDocument, Consumer<Problem> navigate) {
        super(new MigLayout("insets 4, fill, wrap 1", "[grow, fill]", "[][grow, fill][][]"));
        this.navigate = navigate;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                                   boolean selected, boolean focused) {
                super.getListCellRendererComponent(source, value, index, selected, focused);
                // Described at paint time, so a renamed label shows without waiting for the problems to change.
                setText(ProblemText.describe((Problem) value, openDocument.document(), openDocument.registry()));
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                int index = list.locationToIndex(event.getPoint());
                Rectangle cell = index < 0 ? null : list.getCellBounds(index, index);
                if (cell != null && cell.contains(event.getPoint())) {
                    activate(index);
                }
            }
        });
        add(summary);
        add(new JScrollPane(list));
        showProblems(List.of());
        add(libraryHeader);
        add(libraryScroll, "hmax 140");
        showLibraryProblems(List.of());
    }

    public void showSnapshot(SimulationSnapshot snapshot) {
        showProblems(ProblemText.ordered(snapshot.problems()));
    }

    /** Component library files that could not be loaded (spec §5.6); the section hides when there are none. */
    public void showLibraryProblems(List<String> descriptions) {
        showLibraryProblems(descriptions, descriptions.size());
    }

    /**
     * Like {@link #showLibraryProblems(List)}; only {@code fileCount} of the entries are files, so the header does not
     * count the others (a missing library folder) as files.
     */
    public void showLibraryProblems(List<String> descriptions, int fileCount) {
        libraryModel.clear();
        libraryModel.addAll(descriptions);
        libraryHeader.setText(fileCount > 0 || descriptions.isEmpty()
                ? Messages.format("problems.library", fileCount)
                : Messages.text("problems.libraryFolder"));
        boolean visible = !descriptions.isEmpty();
        libraryHeader.setVisible(visible);
        libraryScroll.setVisible(visible);
        revalidate();
    }

    List<String> libraryProblemTexts() {
        return Collections.list(libraryModel.elements());
    }

    JLabel libraryHeader() {
        return libraryHeader;
    }

    JScrollPane libraryScroll() {
        return libraryScroll;
    }

    void activate(int index) {
        if (index >= 0 && index < model.size()) {
            navigate.accept(model.get(index));
        }
    }

    JList<Problem> problemList() {
        return list;
    }

    int refreshCount() {
        return refreshCount;
    }

    String summaryText() {
        return summary.getText();
    }

    private void showProblems(List<Problem> problems) {
        if (problems.equals(shown)) {
            return;
        }
        shown = problems;
        refreshCount++;
        Problem selected = list.getSelectedValue();
        model.clear();
        model.addAll(problems);
        if (selected != null) {
            for (int index = 0; index < problems.size(); index++) {
                if (problems.get(index).netId().equals(selected.netId())) {
                    list.setSelectedIndex(index);
                    break;
                }
            }
        }
        summary.setText(Messages.format("problems.count", problems.size()));
    }
}
