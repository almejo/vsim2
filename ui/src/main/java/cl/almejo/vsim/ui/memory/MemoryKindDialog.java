package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.util.Optional;

/**
 * The question for a memory file without a header (memory spec §8.5): RAM or ROM, «Palabras», «Ancho de palabra»
 * and «Nombre», filled in with what the file suggests. The panel is testable without a window; {@link #ask} shows it.
 */
final class MemoryKindDialog extends JPanel {

    private final JRadioButton ram = new JRadioButton(Messages.componentName(MemoryKind.RAM.id()));
    private final JRadioButton rom = new JRadioButton(Messages.componentName(MemoryKind.ROM.id()));
    private final JComboBox<String> words = new JComboBox<>();
    private final JSpinner width;
    private final JTextField name;

    MemoryKindDialog(MemoryFileImport.Proposal proposal) {
        super(new MigLayout("wrap 2, insets dialog", "[right][grow, fill]"));
        ButtonGroup kinds = new ButtonGroup();
        kinds.add(ram);
        kinds.add(rom);
        (proposal.kind() == MemoryKind.RAM ? ram : rom).setSelected(true);
        for (int bits = 1; bits <= MemoryTypes.MAXIMUM_ADDRESS_BITS; bits++) {
            words.addItem(Messages.choiceName(MemoryTypes.WORDS, String.valueOf(1L << bits)));
        }
        words.setSelectedIndex(Long.numberOfTrailingZeros(proposal.words()) - 1);
        width = new JSpinner(new SpinnerNumberModel(proposal.width(), 1, MemoryTypes.MAXIMUM_WIDTH, 1));
        name = new JTextField(proposal.name(), 16);

        JPanel kind = new JPanel(new MigLayout("insets 0"));
        kind.setOpaque(false);
        kind.add(ram);
        kind.add(rom);
        add(new JLabel(Messages.text("memory.kind.kind")));
        add(kind);
        add(new JLabel(Messages.parameterName(MemoryTypes.WORDS)));
        add(words);
        add(new JLabel(Messages.parameterName(MemoryTypes.WIDTH)));
        add(width);
        add(new JLabel(Messages.text("memory.kind.name")));
        add(name);
    }

    /** What the panel holds now. */
    MemoryFileImport.Proposal result() {
        return new MemoryFileImport.Proposal(ram.isSelected() ? MemoryKind.RAM : MemoryKind.ROM,
                name.getText().strip(), 1L << (words.getSelectedIndex() + 1), (Integer) width.getValue());
    }

    JRadioButton ram() {
        return ram;
    }

    JComboBox<String> words() {
        return words;
    }

    JTextField name() {
        return name;
    }

    /** Shows the question over {@code parent}; empty when cancelled. */
    static Optional<MemoryFileImport.Proposal> ask(java.awt.Component parent, String fileName,
                                                   MemoryFileImport.Proposal proposal) {
        MemoryKindDialog panel = new MemoryKindDialog(proposal);
        int answer = JOptionPane.showConfirmDialog(parent, panel, Messages.format("memory.kind.title", fileName),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? Optional.of(panel.result()) : Optional.empty();
    }
}
