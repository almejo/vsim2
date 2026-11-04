package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The content of the report window: the summary on top and, in a scroll pane, the report: a heading per circuit, a
 * smaller one per kind, each explanation once as wrapping text and under it the places it applies to. A place that is
 * a part of the document is a link-like button that goes to the part ({@link ReportHost#goTo}); the others are plain
 * text. The text wraps to the window's width, so the window can be resized freely.
 */
final class ImportReportPanel extends JPanel {

    private final List<JButton> placeButtons = new ArrayList<>();

    ImportReportPanel(String summary, ImportReport report, ReportHost host) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        add(text(summary), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(content(report, host));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    /** The buttons that go to a part, in the order they are listed. */
    List<JButton> placeButtons() {
        return List.copyOf(placeButtons);
    }

    private JComponent content(ImportReport report, ReportHost host) {
        JPanel content = new WidthTrackingPanel(new MigLayout("insets 0, wrap 1, fillx, gapy 2", "[grow, fill]"));
        for (ImportReport.CircuitGroup circuit : report.circuits()) {
            JLabel circuitLabel = new JLabel(circuit.title());
            circuitLabel.setFont(circuitLabel.getFont().deriveFont(Font.BOLD, circuitLabel.getFont().getSize2D() + 2));
            if (circuit.place().isPresent()) {
                // its own row panel: the content column grows, which would push the link away from the heading
                JPanel heading = new JPanel(new MigLayout("insets 0, gapx 8", "[][]"));
                heading.setOpaque(false);
                heading.add(circuitLabel);
                heading.add(place(circuit.place().get(), host));
                content.add(heading, "gaptop 10, growx 0, alignx left");
            } else {
                content.add(circuitLabel, "gaptop 10");
            }
            for (ImportReport.KindGroup kind : circuit.kinds()) {
                JLabel kindLabel = new JLabel(kind.title());
                kindLabel.setFont(kindLabel.getFont().deriveFont(Font.BOLD));
                content.add(kindLabel, "gaptop 6, gapleft 8");
                for (ImportReport.Entry entry : kind.entries()) {
                    content.add(text(entry.explanation()), "gaptop 4, gapleft 16, wmin 0");
                    for (ImportReport.Place place : entry.places()) {
                        content.add(place(place, host), "gapleft 32");
                    }
                }
            }
        }
        return content;
    }

    private static JTextArea text(String explanation) {
        JTextArea area = new JTextArea(explanation);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        return area;
    }

    private JComponent place(ImportReport.Place place, ReportHost host) {
        if (place.element().isEmpty()) {
            return new JLabel(place.text());
        }
        ElementId element = place.element().get();
        JButton button = new JButton(place.text());
        button.setToolTipText(Messages.text(LogisimImportTexts.PLACE_TOOLTIP));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(button.getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
        button.addActionListener(action -> host.goTo(element));
        placeButtons.add(button);
        return button;
    }

    /** A panel that is as wide as the scroll pane's viewport, so its wrapping text follows the window's width. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        WidthTrackingPanel(MigLayout layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
