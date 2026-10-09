package cl.almejo.vsim.ui.window;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Optional;

/** Right-hand side panel of an editor window: one tab per inspector (Propiedades, Problemas, …). Hidden while empty. */
public final class InspectorPanel extends JPanel {

    public static final int PREFERRED_WIDTH = 260;

    private final JTabbedPane tabs = new JTabbedPane();

    public InspectorPanel() {
        super(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
        setMinimumSize(new Dimension(0, 0));
        setVisible(false);
    }

    public void addTab(String title, JComponent content) {
        if (tabs.indexOfTab(title) >= 0) {
            throw new IllegalArgumentException("duplicate inspector tab " + title);
        }
        tabs.addTab(title, content);
        setVisible(true);
        revalidate();
    }

    /** Selects the tab named {@code title}, re-opening the panel if it has no width (collapsed). */
    public void showTab(String title) {
        int index = tabs.indexOfTab(title);
        if (index < 0) {
            throw new IllegalArgumentException("no inspector tab " + title);
        }
        tabs.setSelectedIndex(index);
        setVisible(true);
        if (getWidth() == 0 && getParent() instanceof JSplitPane split) {
            split.resetToPreferredSizes();
        }
    }

    public int tabCount() {
        return tabs.getTabCount();
    }

    public Optional<String> selectedTab() {
        int index = tabs.getSelectedIndex();
        return index < 0 ? Optional.empty() : Optional.of(tabs.getTitleAt(index));
    }
}
