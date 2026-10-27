package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.ChangeStickyNoteCommand;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The «Notas» inspector tab (spec §3): one row per sticky note of the document in reading order, each with a swatch of
 * its paper and its first line. Choosing a row (click or Enter) selects the note, travels the window's active pane to
 * it and unfolds it when folded (one command).
 */
public final class StickyNotesPanel extends JPanel {

    private static final String LIST_CARD = "list";
    private static final String HINT_CARD = "hint";
    private static final int SWATCH_SIZE = 12;

    private final OpenDocument openDocument;
    /** The window's active pane, resolved when a row is chosen. */
    private final Supplier<CircuitCanvas> canvas;
    private final Supplier<ColorScheme> scheme;
    private final Consumer<String> titleChanged;
    private final DefaultListModel<StickyNoteRow> model = new DefaultListModel<>();
    private final JList<StickyNoteRow> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel hint = new JLabel("<html><body style='width: 200px'>"
            + Messages.text("stickyNotes.empty") + "</body></html>", SwingConstants.CENTER);
    private final Runnable documentListener = this::rebuild;

    /** {@code titleChanged} hears the tab title («Notas (n)») every time the rows are rebuilt. */
    public StickyNotesPanel(OpenDocument openDocument, Supplier<CircuitCanvas> canvas, Supplier<ColorScheme> scheme,
                            Consumer<String> titleChanged) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.canvas = canvas;
        this.scheme = scheme;
        this.titleChanged = titleChanged;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) {
                    activate(model.get(index));
                }
            }
        });
        list.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "chooseNote");
        list.getActionMap().put("chooseNote", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                StickyNoteRow row = list.getSelectedValue();
                if (row != null) {
                    activate(row);
                }
            }
        });
        hint.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        body.add(new JScrollPane(list), LIST_CARD);
        body.add(hint, HINT_CARD);
        add(body, BorderLayout.CENTER);
        openDocument.addChangeListener(documentListener);
        rebuild();
    }

    /** Stops following the shared document: the view closed. */
    public void dispose() {
        openDocument.removeChangeListener(documentListener);
    }

    public static String titleFor(int count) {
        return Messages.format("stickyNotes.tab", count);
    }

    /** Repaints the swatches: the colour scheme changed. */
    public void schemeChanged() {
        list.repaint();
    }

    JLabel hint() {
        return hint;
    }

    List<StickyNoteRow> rows() {
        return Collections.list(model.elements());
    }

    /** Whether the empty hint, not the list, is the card showing. */
    boolean showsHint() {
        return model.isEmpty();
    }

    /** Rebuilds the rows from the document, keeping the chosen note chosen. */
    void rebuild() {
        Optional<StickyNoteRow> chosen = Optional.ofNullable(list.getSelectedValue());
        List<StickyNoteRow> rows = StickyNoteRow.of(openDocument.document().stickyNotes());
        if (!rows.equals(rows())) {
            model.clear();
            rows.forEach(model::addElement);
            chosen.flatMap(previous -> rows.stream().filter(row -> row.id().equals(previous.id())).findFirst())
                    .ifPresent(row -> list.setSelectedValue(row, true));
        }
        cards.show(body, rows.isEmpty() ? HINT_CARD : LIST_CARD);
        titleChanged.accept(titleFor(rows.size()));
    }

    /** Selects the note of {@code row}, travels the active pane to it and unfolds it when folded. */
    void activate(StickyNoteRow row) {
        Optional<StickyNote> found = openDocument.document().stickyNote(row.id());
        if (found.isEmpty()) {
            return;
        }
        StickyNote note = found.get();
        if (note.folded()) {
            note = note.withFolded(false);
            openDocument.execute(new ChangeStickyNoteCommand(found.get(), note));
        }
        CircuitCanvas target = canvas.get();
        target.selection().select(note.id());
        target.travelTo(DrawnBounds.of(note, openDocument.registry()));
    }

    private final class Renderer extends JLabel implements ListCellRenderer<StickyNoteRow> {

        private Color swatch = Color.WHITE;

        Renderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(3, SWATCH_SIZE + 14, 3, 4));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends StickyNoteRow> rows, StickyNoteRow row,
                                                      int index, boolean selected, boolean focused) {
            setText(row.text());
            setToolTipText(row.text());
            swatch = scheme.get().stickyPaper(row.color()); // read at paint time, never cached across schemes
            setBackground(selected ? rows.getSelectionBackground() : rows.getBackground());
            setForeground(selected ? rows.getSelectionForeground() : rows.getForeground());
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, SWATCH_SIZE + 8));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int top = (getHeight() - SWATCH_SIZE) / 2;
            graphics.setColor(swatch);
            graphics.fillRect(6, top, SWATCH_SIZE, SWATCH_SIZE);
            graphics.setColor(swatch.darker());
            graphics.drawRect(6, top, SWATCH_SIZE, SWATCH_SIZE);
        }
    }
}
