package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Preferencias ▸ «IA» (spec §2, §9.1): at the top the AI panel's state line with «Encender» / «Apagar» and
 * «Reiniciar» ({@link AiStateHeader}, when the application's server is given), then «Iniciar con VSim», the port with «Usar el por defecto», «Generar un secreto
 * nuevo», «Copiar configuración para Claude Code» and the data note. Every change is stored at once; a running server
 * follows a new port or secret by itself ({@link AiServerService}). With a port given on the command line for this
 * session it says so and the remembered port waits, disabled, until the next run.
 */
public final class AiPreferencesPanel extends JPanel {

    private static final int MINIMUM_PORT = 1024;
    private static final int MAXIMUM_PORT = 65_535;

    private final AppPreferences preferences;
    private final Consumer<String> clipboard;
    private final JCheckBox startWithVsimBox = new JCheckBox(Messages.text("ai.preferences.startWithVsim"));
    private final JSpinner portSpinner = new JSpinner();
    private final JButton defaultPortButton = new JButton(Messages.text("ai.preferences.defaultPort"));
    private final JButton newSecretButton = new JButton(Messages.text("ai.preferences.newSecret"));
    private final JButton copyButton = new JButton(Messages.text("ai.copyConfiguration"));
    private final JLabel feedback = new JLabel(" ");
    private final JLabel dataNote = new JLabel(Messages.text("ai.dataNote"));
    private final JLabel sessionPortNote = new JLabel(" ");
    private final Runnable preferenceListener = this::followPreferences;
    private final Optional<AiServerService> service;
    private final Runnable removeServiceListener;
    /** The panel's state line with «Encender» / «Apagar» and «Reiniciar», when the application's server is given. */
    private final Optional<AiStateHeader> header;
    private boolean following;

    /**
     * Without the application's server (tests, the remembered port only).
     *
     * @param clipboard where «Copiar configuración» puts the line ({@link #systemClipboard()} in the editor)
     */
    public AiPreferencesPanel(AppPreferences preferences, Consumer<String> clipboard) {
        this(preferences, Optional.empty(), clipboard);
    }

    /**
     * @param service   the application's server: its session port is shown and copied
     * @param clipboard where «Copiar configuración» puts the line ({@link #systemClipboard()} in the editor)
     */
    public AiPreferencesPanel(AppPreferences preferences, AiServerService service, Consumer<String> clipboard) {
        this(preferences, Optional.of(service), clipboard);
    }

    private AiPreferencesPanel(AppPreferences preferences, Optional<AiServerService> service,
                               Consumer<String> clipboard) {
        super(new MigLayout("insets dialog, wrap 2", "[right][grow, fill]"));
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.service = Objects.requireNonNull(service, "service");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");

        portSpinner.setModel(new SpinnerNumberModel(preferences.aiPort(), MINIMUM_PORT, MAXIMUM_PORT, 1));
        // the port is a number, never «47.017»
        portSpinner.setEditor(new JSpinner.NumberEditor(portSpinner, "#"));
        startWithVsimBox.setSelected(preferences.aiStartWithVsim());
        refreshDefaultPortButton();

        startWithVsimBox.addActionListener(event -> preferences.setAiStartWithVsim(startWithVsimBox.isSelected()));
        portSpinner.addChangeListener(event -> {
            if (!following) {
                preferences.setAiPort((Integer) portSpinner.getValue());
            }
        });
        defaultPortButton.addActionListener(event -> preferences.setAiPort(AppPreferences.DEFAULT_AI_PORT));
        newSecretButton.addActionListener(event -> {
            AiServerService.regenerateSecret(preferences);
            feedback.setText(Messages.text("ai.preferences.newSecret.done"));
        });
        copyButton.addActionListener(event -> {
            clipboard.accept(service.map(AiServerService::claudeCodeCommand)
                    .orElseGet(() -> AiServerService.claudeCodeCommand(preferences)));
            feedback.setText(Messages.text("ai.copyConfiguration.done"));
        });
        preferences.addListener(preferenceListener);
        this.removeServiceListener = service.map(server -> server.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                refreshSessionPort();
            }
        })).orElse(() -> { });
        refreshSessionPort();
        this.header = service.map(server -> new AiStateHeader(server, feedback::setText));

        header.ifPresent(stateHeader -> add(stateHeader, "span 2, growx, gapbottom 12"));
        add(startWithVsimBox, "skip 1");
        add(new JLabel(Messages.text("ai.preferences.port")), "gaptop 12");
        add(portSpinner, "gaptop 12, split 2, growx 0, wmin 90");
        add(defaultPortButton, "growx 0");
        add(sessionPortNote, "skip 1, hidemode 3");
        add(new JLabel(Messages.text("ai.preferences.portNote")), "skip 1");
        add(newSecretButton, "skip 1, gaptop 12, growx 0");
        add(copyButton, "skip 1, growx 0");
        add(feedback, "skip 1");
        add(dataNote, "skip 1, gaptop 12");
    }

    /** The system clipboard (never in a headless test). */
    public static Consumer<String> systemClipboard() {
        return text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /** Stops following the preferences: the dialog closed. */
    public void dispose() {
        preferences.removeListener(preferenceListener);
        removeServiceListener.run();
        header.ifPresent(AiStateHeader::dispose);
    }

    /** The port can change elsewhere while the dialog is open («Usar el siguiente libre»). */
    private void followPreferences() {
        following = true;
        try {
            if (!portSpinner.getValue().equals(preferences.aiPort())) {
                portSpinner.setValue(preferences.aiPort());
            }
            startWithVsimBox.setSelected(preferences.aiStartWithVsim());
        } finally {
            following = false;
        }
        refreshDefaultPortButton();
    }

    private void refreshDefaultPortButton() {
        defaultPortButton.setEnabled(!sessionPortGiven() && preferences.aiPort() != AppPreferences.DEFAULT_AI_PORT);
    }

    /** The session port's note, and the remembered port disabled while one is given. */
    private void refreshSessionPort() {
        Optional<String> note = service.flatMap(AiServerService::sessionPortNote);
        sessionPortNote.setText(note.orElse(" "));
        sessionPortNote.setVisible(note.isPresent());
        portSpinner.setEnabled(!sessionPortGiven());
        refreshDefaultPortButton();
    }

    private boolean sessionPortGiven() {
        return service.map(AiServerService::isSessionPort).orElse(false);
    }

    Optional<AiStateHeader> header() {
        return header;
    }

    JCheckBox startWithVsimBox() {
        return startWithVsimBox;
    }

    JSpinner portSpinner() {
        return portSpinner;
    }

    JButton defaultPortButton() {
        return defaultPortButton;
    }

    JButton newSecretButton() {
        return newSecretButton;
    }

    JButton copyButton() {
        return copyButton;
    }

    JLabel feedback() {
        return feedback;
    }

    JLabel sessionPortNote() {
        return sessionPortNote;
    }

    JLabel dataNote() {
        return dataNote;
    }
}
