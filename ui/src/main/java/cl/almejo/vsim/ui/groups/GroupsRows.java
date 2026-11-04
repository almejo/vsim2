package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.window.EditorView;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The group rows of a «Separador de bus» or «Unión de bus» in the «Propiedades» tab (spec §2.3), under «Ancho
 * (bits)»: «Bits por pin» (empty with «Personalizado» when the pins are not equal, «(el último de 1)» next to it when
 * the last pin is smaller; a number typed there gives equal pins, one undo step), «Configuración avanzada…» (the «Grupos» window) and the {@link GroupsSummary summary}. The raw
 * {@code groups} text is a hidden parameter, so the generic rows never show it.
 */
final class GroupsRows implements PropertiesPanel.ComponentRows {

    private static final int BITS_PER_PIN_COLUMNS = 3;

    private final EditorView view;
    private final GroupsWindowLauncher launcher;
    private final Supplier<ColorScheme> scheme;

    GroupsRows(EditorView view, GroupsWindowLauncher launcher) {
        this.view = view;
        this.launcher = launcher;
        this.scheme = () -> view.canvas().scheme();
    }

    @Override
    public void addRows(Component component, PropertiesPanel.Rows rows) {
        if (!GroupsEdit.hasGroups(component)) {
            return;
        }
        BitGroups groups = GroupsEdit.groupsOf(view.openDocument(), component);
        rows.add(Messages.text("groups.bitsPerPin"), bitsPerPinLine(component.id(), groups));
        JButton advanced = new JButton(Messages.text("groups.advanced"));
        advanced.setName("groups.advanced");
        ElementId id = component.id();
        advanced.addActionListener(event -> launcher.open(view, id));
        rows.add("", advanced);
        rows.addWide(summary(groups));
    }

    private JPanel bitsPerPinLine(ElementId id, BitGroups groups) {
        int size = groups.uniformSize();
        JTextField field = new JTextField(size > 0 ? String.valueOf(size) : "", BITS_PER_PIN_COLUMNS);
        field.setName("groups.bitsPerPin");
        field.setToolTipText(Messages.format("groups.bitsPerPinTip", groups.width()));
        Runnable commit = () -> commitBitsPerPin(id, groups, field);
        field.addActionListener(event -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                commit.run();
            }
        });
        JLabel custom = new MutedLabel(size == 0 ? Messages.text("groups.custom")
                : groups.lastPinRemainder() > 0 ? Messages.format("groups.lastPin", groups.lastPinRemainder()) : "");
        custom.setName("groups.custom");
        JPanel line = new JPanel(new MigLayout("insets 0", "[]8[grow]"));
        line.setOpaque(false);
        line.add(field);
        line.add(custom);
        return line;
    }

    /**
     * A number from 1 to «Ancho» gives equal pins of that many bits; anything else, or pins that would join a wire of
     * their own width ({@link GroupsEdit#refusal}), is told in the status bar and the field shows the bits per pin
     * again. The same number, or a field left empty, changes nothing.
     */
    private void commitBitsPerPin(ElementId id, BitGroups groups, JTextField field) {
        String text = field.getText().strip();
        int current = groups.uniformSize();
        if (text.isEmpty() || text.equals(String.valueOf(current))) {
            return;
        }
        OptionalInt size = bitsPerPin(text, groups.width());
        if (size.isEmpty()) {
            field.setText(current > 0 ? String.valueOf(current) : "");
            view.statusBar().showNotice(Messages.format("groups.bitsPerPinTip", groups.width()));
            return;
        }
        BitGroups uniform = BitGroups.uniform(groups.width(), size.getAsInt());
        Optional<String> refusal = GroupsEdit.refusal(view.openDocument(), id, uniform);
        if (refusal.isPresent()) {
            field.setText(current > 0 ? String.valueOf(current) : "");
            view.statusBar().showNotice(refusal.get());
            return;
        }
        GroupsEdit.command(view.openDocument(), id, uniform).ifPresent(view.openDocument()::execute);
    }

    private static OptionalInt bitsPerPin(String text, int width) {
        try {
            int size = Integer.parseInt(text);
            return size >= 1 && size <= width ? OptionalInt.of(size) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private JPanel summary(BitGroups groups) {
        GroupsSummary summary = GroupsSummary.of(groups);
        JPanel panel = new JPanel(new MigLayout("insets 0, wrap 1, gapy 2", "[grow, fill]"));
        panel.setOpaque(false);
        panel.setName("groups.summary");
        panel.add(new MutedLabel(summary.heading()), "wmin 0");
        for (int pin = 0; pin < summary.pinLines().size(); pin++) {
            JLabel line = new JLabel(summary.pinLines().get(pin), new GroupSwatch(pin, scheme), JLabel.LEADING);
            panel.add(line, "wmin 0");
        }
        summary.unused().ifPresent(unused -> panel.add(new MutedLabel(unused), "wmin 0"));
        return panel;
    }
}
