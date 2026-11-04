package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.ScaleMode;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

/**
 * The quick options popup of the summary label (sheets spec §5.5): the paper, the orientation and the scale as radio
 * items, «Saltar hojas vacías» as a check item, then «Más opciones…», which opens Archivo ▸ «Exportar a PDF…» (it
 * starts on «Todo el circuito»). A pick writes the remembered print options at once
 * ({@link AppPreferences#setPrintOptions}, the same keys the dialog reads), so the canvas and the paper keep one
 * configuration; the sheets redraw through the preference listeners. No command, no undo step, the circuit is not
 * modified.
 */
final class SheetsQuickOptions {

    /** The action that opens the print dialog on «Todo el circuito»: «Exportar a PDF…», always there. */
    static final String MORE_OPTIONS_SHORTCUT = "file.exportPdf";
    /** The percentages offered besides «Ajustar a una hoja»; the current custom one is added when it is another. */
    private static final List<Integer> PERCENTAGES = List.of(100, 75, 50);

    private final EditorView view;
    private final AppPreferences preferences;
    private final SheetsFeatures.MenuShower menuShower;
    /** Whether the sheets on the canvas are several: «Saltar hojas vacías» does nothing on one. */
    private final BooleanSupplier severalSheets;

    SheetsQuickOptions(EditorView view, AppPreferences preferences, SheetsFeatures.MenuShower menuShower,
                       BooleanSupplier severalSheets) {
        this.severalSheets = severalSheets;
        this.view = view;
        this.preferences = preferences;
        this.menuShower = menuShower;
    }

    /** Opens the popup on {@code invoker} at ({@code x}, {@code y}), its items showing the remembered options. */
    void show(Component invoker, int x, int y) {
        PrintOptions options = current();
        JPopupMenu menu = new JPopupMenu();
        addGroup(menu, paperItems(options));
        menu.addSeparator();
        addGroup(menu, orientationItems(options));
        menu.addSeparator();
        addGroup(menu, scaleItems(options));
        menu.addSeparator();
        if (severalSheets.getAsBoolean()) {
            menu.add(skipEmptySheetsItem(options));
            menu.addSeparator();
        }
        menu.add(new JMenuItem(Messages.text("sheets.summary.moreOptions"))).addActionListener(event -> moreOptions());
        menuShower.show(menu, invoker, x, y);
    }

    private PrintOptions current() {
        return preferences.printOptions(view.openDocument().displayName());
    }

    private List<JRadioButtonMenuItem> paperItems(PrintOptions options) {
        List<JRadioButtonMenuItem> items = new ArrayList<>();
        for (Paper paper : Paper.values()) {
            items.add(item(Messages.text(paper.messageKey()), options.paper() == paper,
                    chosen -> with(chosen, paper, chosen.orientation(), chosen.scaleMode())));
        }
        return items;
    }

    private List<JRadioButtonMenuItem> orientationItems(PrintOptions options) {
        List<JRadioButtonMenuItem> items = new ArrayList<>();
        for (PageOrientation orientation : PageOrientation.values()) {
            String key = orientation == PageOrientation.PORTRAIT
                    ? "print.orientation.portrait" : "print.orientation.landscape";
            items.add(item(Messages.text(key), options.orientation() == orientation,
                    chosen -> with(chosen, chosen.paper(), orientation, chosen.scaleMode())));
        }
        return items;
    }

    private List<JRadioButtonMenuItem> scaleItems(PrintOptions options) {
        List<JRadioButtonMenuItem> items = new ArrayList<>();
        items.add(scaleItem(Messages.text("print.scale.fit"), new ScaleMode.FitToOnePage(), options));
        List<Integer> percentages = new ArrayList<>(PERCENTAGES);
        if (options.scaleMode() instanceof ScaleMode.Percentage custom && !percentages.contains(custom.percent())) {
            percentages.add(custom.percent());
        }
        for (int percent : percentages) {
            items.add(scaleItem(Messages.format("print.zoom.percent", percent), new ScaleMode.Percentage(percent),
                    options));
        }
        return items;
    }

    private JRadioButtonMenuItem scaleItem(String text, ScaleMode scale, PrintOptions options) {
        return item(text, options.scaleMode().equals(scale),
                chosen -> with(chosen, chosen.paper(), chosen.orientation(), scale));
    }

    /** A radio item that, when picked, remembers the options {@code change} makes of the remembered ones. */
    private JRadioButtonMenuItem item(String text, boolean selected, UnaryOperator<PrintOptions> change) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text, selected);
        item.addActionListener(event -> preferences.setPrintOptions(change.apply(current())));
        return item;
    }

    /** «Saltar hojas vacías», checked as remembered; a pick remembers the other state, like the dialog's check box. */
    private JCheckBoxMenuItem skipEmptySheetsItem(PrintOptions options) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(Messages.text("print.skipEmptySheets"),
                options.skipEmptySheets());
        item.addActionListener(event -> preferences.setPrintOptions(current().withSkipEmptySheets(item.isSelected())));
        return item;
    }

    private static void addGroup(JPopupMenu menu, List<JRadioButtonMenuItem> items) {
        ButtonGroup group = new ButtonGroup();
        for (JRadioButtonMenuItem item : items) {
            group.add(item);
            menu.add(item);
        }
    }

    /** {@code options} with another paper, orientation and scale; everything else as it was. */
    private static PrintOptions with(PrintOptions options, Paper paper, PageOrientation orientation,
                                     ScaleMode scale) {
        return new PrintOptions(options.scope(), paper, orientation, scale, options.withValues(),
                options.blackAndWhite(), options.headerFooter(), options.title(), options.includeStickyNotes(),
                options.sheetGuides(), options.showDate(), options.showFileName(), options.showSheetNumber(),
                options.skipEmptySheets());
    }

    /** «Más opciones…»: the view's «Exportar a PDF…», when it is there and can export. */
    private void moreOptions() {
        Action exportPdf = view.shortcutActions().get(MORE_OPTIONS_SHORTCUT);
        if (exportPdf != null && exportPdf.isEnabled()) {
            exportPdf.actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, MORE_OPTIONS_SHORTCUT));
        }
    }
}
