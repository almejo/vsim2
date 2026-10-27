package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Optional;

/** The accessory of the export dialog: the PNG scale (off while SVG is chosen), the white background and the sticky notes. */
public final class ExportOptionsPanel extends JPanel {

    private static final Integer[] SCALES = {1, 2, 4};

    private final JComboBox<Integer> scale = new JComboBox<>(SCALES);
    private final JCheckBox printBackground = new JCheckBox(Messages.text("export.printBackground"));
    private final JCheckBox includeStickyNotes = new JCheckBox(Messages.text("export.includeStickyNotes"));

    public ExportOptionsPanel(int initialScale, boolean initialPrintBackground, boolean initialIncludeStickyNotes) {
        super(new MigLayout("wrap 1, insets 8", "[]", "[]4[]12[]4[]"));
        scale.setSelectedItem(initialScale);
        scale.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value + "×");
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        printBackground.setSelected(initialPrintBackground);
        add(new JLabel(Messages.text("export.scale")));
        add(scale);
        add(printBackground);
        includeStickyNotes.setSelected(initialIncludeStickyNotes);
        add(includeStickyNotes);
    }

    int scale() {
        return (Integer) scale.getSelectedItem();
    }

    boolean printBackground() {
        return printBackground.isSelected();
    }

    boolean includeStickyNotes() {
        return includeStickyNotes.isSelected();
    }

    void setScaleEnabled(boolean enabled) {
        scale.setEnabled(enabled);
    }

    /** The real dialog: PNG and SVG filters, this panel on the right, overwrite confirmation. */
    public static Optional<ExportChoice> choose(Component parent, Path directory, String suggestedName,
                                                int initialScale, boolean initialPrintBackground,
                                                boolean initialIncludeStickyNotes) {
        JFileChooser chooser = new JFileChooser(directory.toFile());
        chooser.setDialogTitle(Messages.text("export.title"));
        FileNameExtensionFilter png = new FileNameExtensionFilter(Messages.text("export.filter.png"), "png");
        FileNameExtensionFilter svg = new FileNameExtensionFilter(Messages.text("export.filter.svg"), "svg");
        chooser.addChoosableFileFilter(png);
        chooser.addChoosableFileFilter(svg);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(png);
        ExportOptionsPanel options = new ExportOptionsPanel(initialScale, initialPrintBackground,
                initialIncludeStickyNotes);
        chooser.setAccessory(options);
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY,
                event -> options.setScaleEnabled(chooser.getFileFilter() == png));
        chooser.setSelectedFile(directory.resolve(suggestedName).toFile());
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        ExportChoice.Format filterFormat = chooser.getFileFilter() == svg ? ExportChoice.Format.SVG
                : ExportChoice.Format.PNG;
        ExportChoice choice = ExportChoice.of(chooser.getSelectedFile().toPath(), filterFormat, options.scale(),
                options.printBackground(), options.includeStickyNotes());
        return FileDialogs.confirmOverwrite(parent, choice.path()) ? Optional.of(choice) : Optional.empty();
    }
}
