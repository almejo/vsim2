package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The «Archivo de salida» row of a Terminal in the «Propiedades» tab: the file «Guardar salida» writes, read-only, and
 * «Elegir archivo…». The file is set only through the chooser (which confirms replacing an existing file), so a path
 * is never typed or pasted in; choosing one turns «Guardar salida» on and names the file as one command (one undo
 * step), and arms the recording for this session ({@link TerminalOutputRecorder#approve}). The parameter is hidden from
 * the generic form: this row is its editor.
 */
final class TerminalOutputRows implements PropertiesPanel.ComponentRows {

    /** The names of the field and the button, for tests. */
    static final String FILE_NAME = "terminal.outputFile";
    static final String CHOOSE_NAME = "terminal.outputFile.choose";
    private static final String TEXT_EXTENSION = ".txt";
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalOutputRows.class);

    private final OpenDocument document;
    private final TerminalOutputRecorder recorder;
    private final TextFileChooser chooser;
    private final java.awt.Component parent;

    TerminalOutputRows(OpenDocument document, TerminalOutputRecorder recorder, TextFileChooser chooser,
                       java.awt.Component parent) {
        this.document = Objects.requireNonNull(document, "document");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    @Override
    public void addRows(Component component, PropertiesPanel.Rows rows) {
        if (!component.typeId().equals(StandardComponentTypes.TERMINAL)) {
            return;
        }
        String stored = storedFile(component);
        JTextField shown = new JTextField(stored.isBlank() ? Messages.text("terminal.output.noFile")
                : MemoryLinkPaths.displayPath(stored, document.path()));
        shown.setName(FILE_NAME);
        shown.setEditable(false);
        shown.setToolTipText(stored.isBlank() ? null : stored);
        JButton choose = new JButton(Messages.text("terminal.output.choose"));
        choose.setName(CHOOSE_NAME);
        ElementId id = component.id();
        choose.addActionListener(event -> choose(id));
        JPanel line = new JPanel(new MigLayout("insets 0, gapx 4", "[grow, fill][]"));
        line.setOpaque(false);
        line.add(shown, "wmin 0");
        line.add(choose);
        rows.add(Messages.text("parameter.output-file"), line);
    }

    private void choose(ElementId id) {
        document.document().component(id).ifPresent(terminal -> chooser.chooseFile(parent, suggestion(terminal))
                .ifPresent(file -> use(terminal, file.toAbsolutePath().normalize())));
    }

    /** The file the Terminal names now, or «<label>.txt» next to the circuit (in the default folder when unsaved). */
    private String suggestion(Component terminal) {
        String stored = storedFile(terminal);
        if (!stored.isBlank()) {
            try {
                return MemoryLinkPaths.resolve(stored, document.path()).toString();
            } catch (InvalidPathException exception) {
                LOGGER.debug("The output file {} is not a path here", stored, exception);
            }
        }
        String name = (terminal.label().isBlank() ? Messages.componentName(terminal.typeId()) : terminal.label())
                + TEXT_EXTENSION;
        return document.path().map(circuit -> circuit.toAbsolutePath().getParent())
                .map(folder -> folder.resolve(name).toString()).orElse(name);
    }

    private void use(Component terminal, Path file) {
        recorder.approve(file);
        ComponentType type = document.registry().typeOf(terminal);
        Map<String, Object> current = type.resolveParameters(terminal.parameters());
        Map<String, Object> parameters = new HashMap<>(current);
        parameters.put(TerminalTypes.SAVE_OUTPUT, TerminalTypes.SAVE_OUTPUT_TO_FILE);
        parameters.put(TerminalTypes.OUTPUT_FILE, file.toString());
        if (!parameters.equals(current)) {
            document.execute(new ChangeParametersCommand(terminal.id(), type, parameters));
        }
    }

    private String storedFile(Component terminal) {
        return String.valueOf(document.registry().typeOf(terminal).resolveParameters(terminal.parameters())
                .getOrDefault(TerminalTypes.OUTPUT_FILE, ""));
    }
}
