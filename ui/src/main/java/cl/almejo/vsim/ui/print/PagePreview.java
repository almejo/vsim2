package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The preview of «Exportar a PDF…» (spec §3.5): a page map (the circuit with the sheet grid over it, the selected
 * sheet highlighted; click or arrow keys to pick a sheet) and the selected sheet enlarged with its own zoom. Both are
 * painted by the same {@link PagePainter} as the PDF, so the preview is the PDF; only the map and the visible sheet are
 * painted, never every sheet. Sheets are white whatever the theme; everything around them follows the theme.
 */
final class PagePreview extends JPanel {

    /** Above this many sheets the map shows only the circuit (the export refuses them anyway). */
    static final int MAXIMUM_OUTLINED_SHEETS = 1000;
    private static final int MAP_PADDING = 8;
    private static final int SHEET_PADDING = 12;

    /** The enlarged sheet's zoom: fitted to the space, or points to screen pixels. */
    enum Zoom {
        FIT(0), ONE_HUNDRED(1), TWO_HUNDRED(2), FOUR_HUNDRED(4);

        private final double factor;

        Zoom(double factor) {
            this.factor = factor;
        }

        @Override
        public String toString() {
            return this == FIT ? Messages.text("print.zoom.fit")
                    : Messages.format("print.zoom.percent", Math.round(factor * 100));
        }
    }

    private final SheetMap map = new SheetMap();
    private final SheetView sheet = new SheetView();
    private final JButton previous = new JButton(Messages.text("print.sheet.previous"));
    private final JButton next = new JButton(Messages.text("print.sheet.next"));
    private final JLabel sheetLabel = new JLabel("", SwingConstants.CENTER);
    private final JComboBox<Zoom> zoom = new JComboBox<>(Zoom.values());
    private Optional<PagePainter> painter = Optional.empty();
    private Optional<PageLayout> layout = Optional.empty();
    private int selected;

    PagePreview() {
        super(new MigLayout("wrap 1, insets 0, gap 6", "[grow, fill, 300::]", "[180!][grow, fill, 220::][]"));
        add(map);
        JScrollPane scroll = new JScrollPane(sheet);
        scroll.getAccessibleContext().setAccessibleName(Messages.text("print.preview.sheet"));
        add(scroll);
        JPanel controls = new JPanel(new MigLayout("insets 0", "[][grow, fill][]push[][]"));
        controls.add(previous);
        controls.add(sheetLabel);
        controls.add(next);
        JLabel zoomLabel = new JLabel(Messages.text("print.zoom"));
        zoomLabel.setLabelFor(zoom);
        controls.add(zoomLabel);
        controls.add(zoom);
        add(controls);
        previous.addActionListener(event -> selectSheet(selected - 1));
        next.addActionListener(event -> selectSheet(selected + 1));
        zoom.addActionListener(event -> {
            sheet.revalidate();
            sheet.repaint();
        });
        updateControls();
    }

    /** Shows the sheets of {@code shown} laid out by {@code shownLayout}; keeps the selected sheet when it exists. */
    void show(PagePainter shown, PageLayout shownLayout) {
        painter = Optional.of(shown);
        layout = Optional.of(shownLayout);
        int count = shownLayout.pageCount();
        selected = count == 0 ? 0 : Math.min(selected, count - 1);
        changed();
    }

    /** Shows nothing (no area, or a layout that has no room). */
    void clear() {
        painter = Optional.empty();
        layout = Optional.empty();
        selected = 0;
        changed();
    }

    Optional<PagePainter> painter() {
        return painter;
    }

    int selectedSheet() {
        return selected;
    }

    private int sheetCount() {
        return layout.map(PageLayout::pageCount).orElse(0);
    }

    /** Selects sheet {@code index}, kept inside the sheets. */
    void selectSheet(int index) {
        int count = sheetCount();
        if (count == 0) {
            return;
        }
        int clamped = Math.max(0, Math.min(count - 1, index));
        if (clamped != selected) {
            selected = clamped;
            changed();
        }
    }

    private void changed() {
        updateControls();
        map.repaint();
        sheet.revalidate();
        sheet.repaint();
    }

    private void updateControls() {
        int count = sheetCount();
        previous.setEnabled(count > 0 && selected > 0);
        next.setEnabled(count > 0 && selected < count - 1);
        sheetLabel.setText(count == 0 ? "" : Messages.format("print.sheet.number", selected + 1, count));
    }

    String sheetLabel() {
        return sheetLabel.getText();
    }

    JButton previousButton() {
        return previous;
    }

    JButton nextButton() {
        return next;
    }

    SheetMap map() {
        return map;
    }

    SheetView sheet() {
        return sheet;
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    /** The circuit miniature with the sheet grid; one sheet highlighted. */
    final class SheetMap extends JComponent {

        /** What the cached overview was painted for: a new painter, layout, size or screen scale paints it again. */
        private record OverviewKey(PagePainter painter, PageLayout layout, int width, int height, double deviceScale) {
        }

        private OverviewKey overviewKey;
        private BufferedImage overview;
        private int overviewRenders;

        SheetMap() {
            setFocusable(true);
            setToolTipText(Messages.text("print.preview.mapTip"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    select(event.getPoint());
                }
            });
            bindKey(KeyEvent.VK_LEFT);
            bindKey(KeyEvent.VK_RIGHT);
            bindKey(KeyEvent.VK_UP);
            bindKey(KeyEvent.VK_DOWN);
        }

        private void bindKey(int keyCode) {
            String name = "sheet." + keyCode;
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
            getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    key(keyCode);
                }
            });
        }

        /** An arrow key: left and right move one sheet, up and down one row. */
        void key(int keyCode) {
            int columns = layout.map(PageLayout::columns).orElse(1);
            switch (keyCode) {
                case KeyEvent.VK_LEFT -> selectSheet(selected - 1);
                case KeyEvent.VK_RIGHT -> selectSheet(selected + 1);
                case KeyEvent.VK_UP -> {
                    if (selected - columns >= 0) {
                        selectSheet(selected - columns);
                    }
                }
                case KeyEvent.VK_DOWN -> {
                    if (selected + columns < sheetCount()) {
                        selectSheet(selected + columns);
                    }
                }
                default -> { }
            }
        }

        void select(Point2D point) {
            sheetAt(point).ifPresent(PagePreview.this::selectSheet);
        }

        /** The sheet under {@code point} (map pixels): the one whose centre is nearest among those that hold it. */
        OptionalInt sheetAt(Point2D point) {
            if (layout.isEmpty() || !outlined(layout.get())) {
                return OptionalInt.empty();
            }
            Point2D grid = toGrid(point);
            int found = -1;
            double nearest = Double.MAX_VALUE;
            for (int index = 0; index < layout.get().pageCount(); index++) {
                Rectangle2D rectangle = layout.get().page(index).gridRectangle();
                if (rectangle.contains(grid)) {
                    double distance = grid.distance(rectangle.getCenterX(), rectangle.getCenterY());
                    if (distance < nearest) {
                        nearest = distance;
                        found = index;
                    }
                }
            }
            return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
        }

        private boolean outlined(PageLayout shown) {
            return shown.pageCountExact() > 0 && shown.pageCountExact() <= MAXIMUM_OUTLINED_SHEETS;
        }

        /** What the map shows, in grid units: the area and every sheet. */
        private Rectangle2D world() {
            Rectangle2D world = new Rectangle2D.Double();
            painter.ifPresent(shown -> world.setRect(shown.gridArea()));
            layout.filter(this::outlined).ifPresent(shown -> {
                world.add(shown.page(0).gridRectangle());
                world.add(shown.page(shown.pageCount() - 1).gridRectangle());
            });
            return world;
        }

        /** Map pixels per grid unit. */
        private double mapScale(Rectangle2D world) {
            double width = Math.max(1, getWidth() - 2 * MAP_PADDING);
            double height = Math.max(1, getHeight() - 2 * MAP_PADDING);
            return Math.min(width / Math.max(world.getWidth(), 1e-9), height / Math.max(world.getHeight(), 1e-9));
        }

        private Point2D origin(Rectangle2D world, double scale) {
            return new Point2D.Double((getWidth() - world.getWidth() * scale) / 2,
                    (getHeight() - world.getHeight() * scale) / 2);
        }

        /** Where the grid point ({@code gridX}, {@code gridY}) is on the map. */
        Point2D toMap(double gridX, double gridY) {
            Rectangle2D world = world();
            double scale = mapScale(world);
            Point2D origin = origin(world, scale);
            return new Point2D.Double(origin.getX() + (gridX - world.getX()) * scale,
                    origin.getY() + (gridY - world.getY()) * scale);
        }

        private Point2D toGrid(Point2D point) {
            Rectangle2D world = world();
            double scale = mapScale(world);
            Point2D origin = origin(world, scale);
            return new Point2D.Double(world.getX() + (point.getX() - origin.getX()) / scale,
                    world.getY() + (point.getY() - origin.getY()) / scale);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(360, 180);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setColor(uiColor("Panel.background", getBackground()));
                canvas.fillRect(0, 0, getWidth(), getHeight());
                if (painter.isEmpty() || layout.isEmpty()) {
                    return;
                }
                PageLayout shownLayout = layout.get();
                canvas.drawImage(overview(painter.get(), shownLayout, canvas.getTransform().getScaleX()), 0, 0,
                        getWidth(), getHeight(), null);
                if (outlined(shownLayout)) {
                    paintSheets(canvas, shownLayout);
                }
                if (isFocusOwner()) {
                    canvas.setColor(uiColor("Component.focusColor", Color.BLUE));
                    canvas.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                }
            } finally {
                canvas.dispose();
            }
        }

        /**
         * The circuit's miniature at the screen's resolution, painted again only when what it shows changes: focus,
         * hover and selection repaint the map without painting the whole circuit again on the EDT.
         */
        private BufferedImage overview(PagePainter shown, PageLayout shownLayout, double deviceScale) {
            OverviewKey key = new OverviewKey(shown, shownLayout, getWidth(), getHeight(), deviceScale);
            if (key.equals(overviewKey)) {
                return overview;
            }
            overviewRenders++;
            BufferedImage image = new BufferedImage(Math.max(1, (int) Math.ceil(getWidth() * deviceScale)),
                    Math.max(1, (int) Math.ceil(getHeight() * deviceScale)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D miniature = image.createGraphics();
            try {
                miniature.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                miniature.scale(deviceScale, deviceScale);
                Rectangle2D area = shown.gridArea();
                Point2D areaCorner = toMap(area.getX(), area.getY());
                double scale = mapScale(world());
                miniature.translate(areaCorner.getX(), areaCorner.getY());
                miniature.scale(scale / (GridPoint.PIXELS_PER_UNIT * shownLayout.scale()),
                        scale / (GridPoint.PIXELS_PER_UNIT * shownLayout.scale()));
                shown.paintOverview(miniature);
            } finally {
                miniature.dispose();
            }
            overviewKey = key;
            overview = image;
            return image;
        }

        /** How many times the miniature was painted (for tests). */
        int overviewRenders() {
            return overviewRenders;
        }

        private void paintSheets(Graphics2D canvas, PageLayout shownLayout) {
            Color outline = uiColor("Component.borderColor", Color.GRAY);
            Color accent = uiColor("Component.accentColor", uiColor("List.selectionBackground", Color.BLUE));
            canvas.setStroke(new BasicStroke(1f));
            for (int index = 0; index < shownLayout.pageCount(); index++) {
                if (index != selected) {
                    canvas.setColor(outline);
                    canvas.draw(mapRectangle(shownLayout.page(index).gridRectangle()));
                }
            }
            Rectangle2D chosen = mapRectangle(shownLayout.page(selected).gridRectangle());
            if (shownLayout.pageCount() > 1) {
                canvas.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 32));
                canvas.fill(chosen);
            }
            canvas.setColor(accent);
            canvas.setStroke(new BasicStroke(2f));
            canvas.draw(chosen);
        }

        private Rectangle2D mapRectangle(Rectangle2D grid) {
            Point2D corner = toMap(grid.getX(), grid.getY());
            Point2D opposite = toMap(grid.getMaxX(), grid.getMaxY());
            return new Rectangle2D.Double(corner.getX(), corner.getY(), opposite.getX() - corner.getX(),
                    opposite.getY() - corner.getY());
        }
    }

    /** The selected sheet, enlarged. */
    final class SheetView extends JComponent implements Scrollable {

        private Zoom zoom() {
            return (Zoom) zoom.getSelectedItem();
        }

        private Optional<PdfPages.PageSize> pageSize() {
            return painter.filter(shown -> !shown.pages().isEmpty()).map(shown -> shown.pages().get(0).size());
        }

        private double factor(PdfPages.PageSize size) {
            if (zoom() != Zoom.FIT) {
                return zoom().factor;
            }
            double width = Math.max(1, getWidth() - 2 * SHEET_PADDING);
            double height = Math.max(1, getHeight() - 2 * SHEET_PADDING);
            return Math.min(width / size.widthPoints(), height / size.heightPoints());
        }

        @Override
        public Dimension getPreferredSize() {
            Optional<PdfPages.PageSize> size = pageSize();
            if (size.isEmpty() || zoom() == Zoom.FIT) {
                return new Dimension(300, 260);
            }
            return new Dimension((int) Math.ceil(size.get().widthPoints() * zoom().factor) + 2 * SHEET_PADDING,
                    (int) Math.ceil(size.get().heightPoints() * zoom().factor) + 2 * SHEET_PADDING);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setColor(uiColor("Panel.background", getBackground()));
                canvas.fillRect(0, 0, getWidth(), getHeight());
                Optional<PdfPages.PageSize> size = pageSize();
                if (size.isEmpty()) {
                    return;
                }
                double factor = factor(size.get());
                double width = size.get().widthPoints() * factor;
                double height = size.get().heightPoints() * factor;
                double left = Math.max(SHEET_PADDING, (getWidth() - width) / 2);
                double top = Math.max(SHEET_PADDING, (getHeight() - height) / 2);
                canvas.setColor(uiColor("Component.borderColor", Color.GRAY));
                canvas.draw(new Rectangle2D.Double(left - 1, top - 1, width + 1, height + 1));
                canvas.translate(left, top);
                canvas.scale(factor, factor);
                canvas.clip(new Rectangle2D.Double(0, 0, size.get().widthPoints(), size.get().heightPoints()));
                painter.get().paint(canvas, selected);
            } finally {
                canvas.dispose();
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(300, 260);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRectangle, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRectangle, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRectangle.height : visibleRectangle.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return zoom() == Zoom.FIT;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return zoom() == Zoom.FIT;
        }
    }
}
