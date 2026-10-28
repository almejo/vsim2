package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The block under a multi-bit Switch's «Valor» that says how VSim reads the number, one reading under the other
 * (binary, hex and decimal, the decimal signed with «Números: Con signo»), or why it cannot («No cabe en 8 bits»). It is updated on
 * every keystroke and whenever the width changes, and it is never wider than the «Valor» field: a binary too long
 * for it wraps in lines of fewer groups of 4 bits (a 32-bit one in two lines of 16). With one bit (the 1/0/Z list)
 * there is no block. The colours are asked at every paint.
 */
final class SwitchValueReading {

    /** The space between a caption and its value, in pixels. */
    private static final int GAP = 6;

    private final ComponentType type;
    private final Map<String, Object> initial;
    private final JComboBox<?> valueBox;
    private final JSpinner widthSpinner;
    private final JComboBox<?> signednessBox;
    private final JLabel caption = new JLabel();
    private final JPanel block = new JPanel(new MigLayout("insets 0, gap " + GAP + " 0, wrap 2", "[][grow, fill]"));
    private String shown = "";

    SwitchValueReading(ComponentType type, Map<String, Object> initial, JComboBox<?> valueBox, JSpinner widthSpinner,
                       JComboBox<?> signednessBox) {
        this.type = type;
        this.initial = initial;
        this.valueBox = valueBox;
        this.widthSpinner = widthSpinner;
        this.signednessBox = signednessBox;
        block.setName("value-reading");
        block.setOpaque(false);
        DocumentListener refreshOnEdit = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refresh();
            }
        };
        ((JTextComponent) valueBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(refreshOnEdit);
        valueBox.addActionListener(event -> refresh());
        signednessBox.addActionListener(event -> refresh());
        valueBox.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refresh();
            }
        });
        JFormattedTextField widthField = ((JSpinner.DefaultEditor) widthSpinner.getEditor()).getTextField();
        widthField.getDocument().addDocumentListener(refreshOnEdit);
        widthField.addPropertyChangeListener("value", event -> refresh());
        refresh();
    }

    JPanel block() {
        return block;
    }

    /** Adds the empty caption cell and the block as one more row of a two column grid; a hidden block takes no room. */
    void addTo(JPanel host) {
        host.add(caption, "hidemode 3");
        host.add(block, "hidemode 3, growx, wmin 0");
    }

    private void refresh() {
        OptionalLong width = width();
        boolean wide = width.isPresent() && width.getAsLong() > 1;
        caption.setVisible(wide);
        block.setVisible(wide);
        if (!wide) {
            return;
        }
        String text = String.valueOf(valueBox.getEditor().getItem()).strip();
        Optional<ParameterProblem> found = problemWith((int) width.getAsLong(), text);
        int available = valueBox.getWidth();
        String signature = found.map(this::problemText).orElse("") + "|" + text + "|" + width.getAsLong() + "|"
                + available + "|" + signed();
        if (signature.equals(shown)) {
            return;
        }
        shown = signature; 
        block.removeAll();
        if (found.isPresent()) {
            addProblem(problemText(found.get()), available);
        } else {
            addReadings(text, (int) width.getAsLong(), available);
        }
        block.revalidate();
        block.repaint();
    }

    /** The message in the error colour: one line, or several (cut at spaces) when it is wider than the field. */
    private void addProblem(String message, int available) {
        JLabel measure = line(true);
        FontMetrics metrics = measure.getFontMetrics(measure.getFont());
        StringBuilder current = new StringBuilder();
        for (String word : message.split(" ")) {
            if (available > 0 && current.length() > 0 && metrics.stringWidth(current + " " + word) > available) {
                addProblemLine(current.toString());
                current.setLength(0);
            }
            current.append(current.length() == 0 ? "" : " ").append(word);
        }
        addProblemLine(current.toString());
    }

    private void addProblemLine(String text) {
        JLabel line = line(true);
        line.setText(text);
        block.add(line, "span 2, wmin 0");
    }

    private boolean signed() {
        return StandardComponentTypes.SIGNEDNESS_SIGNED.equals(signednessBox.getSelectedItem());
    }

    /** Binary as the multi-bit Switch draws it, hex and the decimal: signed with «Con signo», unsigned otherwise. */
    private void addReadings(String text, int width, int available) {
        long bits = ConstantValue.bitsOf(text, width).orElseThrow();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{Messages.text("switch.reading.binary"), BusValueText.binary(Value.of(width, bits))});
        rows.add(new String[]{Messages.text("switch.reading.hex"),
                ConstantValue.display(bits, width, ConstantValue.HEX)});
        rows.add(signed()
                ? new String[]{Messages.text("switch.reading.signed"),
                        ConstantValue.display(bits, width, ConstantValue.SIGNED)}
                : new String[]{Messages.text("switch.reading.decimal"),
                        ConstantValue.display(bits, width, ConstantValue.DECIMAL)});
        JLabel measure = line(false);
        int captionWidth = 0;
        for (String[] row : rows) {
            captionWidth = Math.max(captionWidth, measure.getFontMetrics(measure.getFont()).stringWidth(row[0]));
        }
        int valueSpace = available > 0 ? available - captionWidth - GAP : Integer.MAX_VALUE;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String[] row = rows.get(rowIndex);
            List<String> lines = rowIndex == 0 ? binaryLines(row[1], measure, valueSpace) : List.of(row[1]);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                JLabel captionLabel = line(false);
                captionLabel.setText(lineIndex == 0 ? row[0] : "");
                JLabel valueLabel = line(false);
                valueLabel.setText(lines.get(lineIndex));
                block.add(captionLabel);
                block.add(valueLabel, "wmin 0");
            }
        }
    }

    /** The binary on one line, or on lines of 4, 2 or 1 groups of bits (the first that fits; the last if none). */
    private static List<String> binaryLines(String binary, JLabel measure, int space) {
        List<String> groups = List.of(binary.split(" "));
        List<Integer> candidates = new ArrayList<>(List.of(groups.size()));
        for (int perLine : new int[]{4, 2, 1}) {
            if (perLine < groups.size()) {
                candidates.add(perLine);
            }
        }
        List<String> lines = List.of(binary);
        for (int perLine : candidates) {
            lines = new ArrayList<>();
            for (int from = 0; from < groups.size(); from += perLine) {
                lines.add(String.join(" ", groups.subList(from, Math.min(groups.size(), from + perLine))));
            }
            boolean fits = lines.stream()
                    .allMatch(line -> measure.getFontMetrics(measure.getFont()).stringWidth(line) <= space);
            if (fits) {
                return lines;
            }
        }
        return lines;
    }

    /** A label of the block, muted (or in the error colour); the colour is asked at every paint. */
    private static JLabel line(boolean problem) {
        return new JLabel() {
            @Override
            public Color getForeground() {
                return problem ? ReadableColors.errorText() : ReadableColors.mutedText();
            }
        };
    }

    /** The width as typed now (the spinner is not committed on every keystroke); empty when it is not a number. */
    private OptionalLong width() {
        JFormattedTextField field = ((JSpinner.DefaultEditor) widthSpinner.getEditor()).getTextField();
        try {
            return OptionalLong.of(Long.parseLong(field.getText().strip()));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private Optional<ParameterProblem> problemWith(int width, String text) {
        Map<String, Object> values = new LinkedHashMap<>(initial);
        values.put(StandardComponentTypes.WIDTH, width);
        values.put(StandardComponentTypes.SIGNEDNESS, signednessBox.getSelectedItem());
        values.put(ParameterForm.SWITCH_VALUE, text);
        return type.check(values);
    }

    private String problemText(ParameterProblem found) {
        return Messages.format(found.messageKey(), found.arguments().toArray());
    }
}
