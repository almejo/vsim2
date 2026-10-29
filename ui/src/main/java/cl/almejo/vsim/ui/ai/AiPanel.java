package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Herramientas ▸ «IA (MCP)…» of one window (spec §2, §7, §9.1; IA.3 «a proper AI panel»): a header with the server's
 * state as a coloured dot and one word (Apagada / Encendiendo… / Escuchando / Conectado / Trabajando / Error), its
 * sentence, the address, since when it runs and the requests served; «Encender» / «Apagar» and «Reiniciar» (the
 * {@link AiStateHeader} Preferencias ▸ «IA» shows too); on an
 * error what to do, with «Usar el siguiente libre» for a taken port; the running tasks with «Detener» and «Deshacer lo
 * que hizo la IA»; the folded «Ajustes que tocó la IA» of the circuit's window (also from a second window of the
 * circuit), the AI's preference proposals with «Aceptar» / «No»; the configuration (port, secret, «Copiar
 * configuración») folded below and the data note. Buttons carry an icon and their text. The AI's texts are plain
 * text, never HTML; colours are read when painted. EDT only.
 */
public final class AiPanel extends JPanel {

    private final AiServerService service;
    private final AppPreferences windowPreferences;
    private final Supplier<Optional<SettingsOverride>> circuitOverride;
    private final PreferenceProposals proposals;
    private final Consumer<String> clipboard;
    /** The state line with «Encender» / «Apagar» and «Reiniciar», shared with Preferencias ▸ «IA». */
    private final AiStateHeader header;
    private final JLabel helpLabel = new JLabel(" ");
    private final JButton nextFreePortButton = new JButton(Messages.text("ai.panel.useNextFreePort"));
    private final JLabel portNote = new JLabel(" ");
    private final JLabel notice = new JLabel(" ");
    private final JPanel tasksSection = new JPanel(new MigLayout("insets 0, wrap 3, gap 6 2", "[grow, fill][][]"));
    private final List<JLabel> taskLabels = new ArrayList<>();
    private final Map<String, JButton> stopButtons = new HashMap<>();
    private final Map<String, JButton> undoButtons = new HashMap<>();
    private final ChangedSettingsSection settingsSection = new ChangedSettingsSection("insets 0, wrap 1",
            "insets 0 16 0 0, wrap 1, gap 2", "", JLabel::new, () -> { });
    private final JButton keepSettingsButton = new JButton(Messages.text("ai.panel.settings.keep"));
    private final JButton dropSettingsButton = new JButton(Messages.text("ai.panel.settings.drop"));
    private final JPanel proposalsSection = new JPanel(new MigLayout("insets 0, wrap 3", "[grow, fill][][]"));
    private final JToggleButton configurationHeader = new JToggleButton(Messages.text("ai.panel.configuration"));
    private final JPanel configurationSection = new JPanel(new MigLayout("insets 0 16 0 0, wrap 1, gap 2",
            "[grow, fill]"));
    private final JLabel portLabel = new JLabel(" ");
    private final JLabel secretLabel = new JLabel(Messages.text("ai.panel.secret"));
    private final JButton copyButton = AiIcons.on(new JButton(Messages.text("ai.copyConfiguration")),
            AiIcons.CLIPBOARD);
    private final JLabel dataNote = new JLabel(Messages.text("ai.dataNote"));
    private final List<JLabel> proposalLabels = new ArrayList<>();
    private final Map<String, JButton> acceptButtons = new HashMap<>();
    private final Map<String, JButton> declineButtons = new HashMap<>();
    private final Runnable removeServiceListener;
    private final Runnable removeProposalsListener;
    private final Runnable preferencesListener = this::refreshSettings;
    /** The preferences of the circuit's window whose override the panel shows, followed while shown. */
    private AppPreferences followedPreferences;
    private String noticeText = " ";
    private boolean disposed;

    /**
     * The panel of a window that is the circuit's only one: its «Ajustes que tocó la IA» are its own.
     *
     * @param windowPreferences the preferences of this panel's window
     * @param clipboard         where «Copiar configuración» puts the line
     */
    public AiPanel(AiServerService service, AppPreferences windowPreferences, PreferenceProposals proposals,
                   Consumer<String> clipboard) {
        this(service, windowPreferences, () -> SettingsOverride.of(windowPreferences), proposals, clipboard);
    }

    /**
     * @param windowPreferences the preferences of this panel's window
     * @param circuitOverride   the override of the window the AI works in for this window's circuit (its first
     *                          window, IA.1 M10), asked again each time the panel refreshes
     * @param clipboard         where «Copiar configuración» puts the line
     */
    public AiPanel(AiServerService service, AppPreferences windowPreferences,
                   Supplier<Optional<SettingsOverride>> circuitOverride, PreferenceProposals proposals,
                   Consumer<String> clipboard) {
        super(new MigLayout("insets dialog, wrap 1, width 480", "[grow, fill]"));
        this.service = Objects.requireNonNull(service, "service");
        this.windowPreferences = Objects.requireNonNull(windowPreferences, "windowPreferences");
        this.circuitOverride = Objects.requireNonNull(circuitOverride, "circuitOverride");
        this.proposals = Objects.requireNonNull(proposals, "proposals");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.header = new AiStateHeader(service, this::showNotice);

        bindButtons();
        layOut();

        this.removeServiceListener = service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                refreshState();
                refreshSettings();
            }

            @Override
            public void requestServed() {
                refreshState();
            }
        });
        this.removeProposalsListener = proposals.addListener(this::refreshProposals);
        windowPreferences.addListener(preferencesListener);
        refresh();
    }

    /** What the header's buttons say (a blank clears it). */
    private void showNotice(String text) {
        noticeText = text;
        refreshState();
    }

    private void bindButtons() {
        nextFreePortButton.addActionListener(event -> {
            int taken = service.effectivePort();
            if (!service.useNextFreePort()) {
                noticeText = Messages.format("ai.panel.noFreePort", Integer.toString(taken));
                refreshState();
            }
        });
        copyButton.addActionListener(event -> {
            clipboard.accept(service.claudeCodeCommand());
            noticeText = Messages.text("ai.copyConfiguration.done");
            service.configurationCopied();
            refreshState();
        });
        keepSettingsButton.addActionListener(event -> circuitOverride.get().ifPresent(SettingsOverride::keep));
        dropSettingsButton.addActionListener(event -> circuitOverride.get()
                .ifPresent(SettingsOverride::dropEditorSettings));
        configurationHeader.addActionListener(event -> showConfiguration(configurationHeader.isSelected()));
    }

    private void layOut() {
        helpLabel.putClientProperty("html.disable", Boolean.TRUE);
        add(header);
        add(helpLabel, "hidemode 3, gaptop 6");
        add(nextFreePortButton, "growx 0, hidemode 3");
        add(portNote, "hidemode 3");
        add(notice);
        add(tasksSection, "gaptop 8, hidemode 3");
        settingsSection.add(keepSettingsButton, "split 2, growx 0");
        settingsSection.add(dropSettingsButton, "growx 0");
        add(settingsSection, "gaptop 12, hidemode 3");
        add(proposalsSection, "gaptop 12, hidemode 3");
        configurationSection.add(portLabel);
        configurationSection.add(secretLabel);
        configurationSection.add(copyButton, "growx 0, gaptop 4");
        configurationSection.setVisible(false);
        add(configurationHeader, "growx 0, gaptop 12");
        add(configurationSection, "hidemode 3");
        add(dataNote, "gaptop 8");
    }

    /** Everything again, with the circuit's override asked again (the panel is shown). */
    public void refresh() {
        refreshState();
        refreshSettings();
        refreshProposals();
    }

    /** Stops following the service, the proposals and the preferences: the panel closed. */
    public void dispose() {
        disposed = true;
        header.dispose();
        removeServiceListener.run();
        removeProposalsListener.run();
        windowPreferences.removeListener(preferencesListener);
        follow(null);
    }

    private void refreshState() {
        AiServerState state = service.state();
        header.refresh();
        Optional<String> help = helpText(state);
        helpLabel.setText(help.orElse(" "));
        helpLabel.setVisible(help.isPresent());
        nextFreePortButton.setVisible(state instanceof AiServerState.PortInUse);
        Optional<String> sessionNote = service.sessionPortNote();
        portNote.setText(sessionNote.orElse(" "));
        portNote.setVisible(sessionNote.isPresent());
        notice.setText(service.portChanged() ? Messages.text("ai.panel.portChanged") : noticeText);
        if (service.portChanged()) {
            showConfiguration(true); // the line to copy again is there
        }
        portLabel.setText(Messages.format("ai.panel.port", Integer.toString(service.effectivePort())));
        refreshTasks();
        revalidate();
        repaint();
    }

    private static Optional<String> helpText(AiServerState state) {
        return switch (state) {
            case AiServerState.PortInUse taken ->
                    Optional.of(Messages.format("ai.panel.help.portInUse", Integer.toString(taken.port())));
            case AiServerState.OtherVsim other ->
                    Optional.of(Messages.format("ai.panel.help.otherVsim", Integer.toString(other.port())));
            case AiServerState.Failed failed -> Optional.of(Messages.text("ai.panel.help.failed"));
            default -> Optional.empty();
        };
    }

    private void refreshTasks() {
        tasksSection.removeAll();
        taskLabels.clear();
        stopButtons.clear();
        undoButtons.clear();
        List<TaskInfo> running = service.runningTasks();
        tasksSection.setVisible(!running.isEmpty());
        if (!running.isEmpty()) {
            tasksSection.add(new JLabel(Messages.text("ai.panel.tasks")), "span 3");
        }
        for (TaskInfo task : running) {
            JLabel title = plainText(task.title());
            JButton stop = AiIcons.on(new JButton(Messages.text("ai.status.stop")), AiIcons.STOP);
            JButton undo = AiIcons.on(new JButton(Messages.text("ai.status.undo")), AiIcons.UNDO);
            stop.addActionListener(event -> service.stopTask(task.taskId()));
            undo.addActionListener(event -> service.undoTask(task.taskId(), this::undone));
            taskLabels.add(title);
            stopButtons.put(task.taskId(), stop);
            undoButtons.put(task.taskId(), undo);
            tasksSection.add(title, "gapleft 16");
            tasksSection.add(stop);
            tasksSection.add(undo);
        }
    }

    private void undone(UndoOutcome outcome) {
        if (disposed) {
            return;
        }
        noticeText = AiUndoNotices.of(outcome);
        refreshState();
    }

    private void showConfiguration(boolean shown) {
        configurationHeader.setSelected(shown);
        configurationSection.setVisible(shown);
        revalidate();
        repaint();
    }

    private void refreshSettings() {
        Optional<SettingsOverride> override = circuitOverride.get();
        follow(override.map(SettingsOverride::windowPreferences).orElse(null));
        List<SettingsOverride.Change> changes = override.map(SettingsOverride::changes).orElse(List.of());
        settingsSection.showChanges(changes.stream().map(change -> Messages.format("ai.setting.change",
                settingName(change.setting()), valueText(change.value()))).toList());
        revalidate();
        repaint();
    }

    /** Hears the circuit's window too when it is another one than this panel's (IA.1 M10). */
    private void follow(AppPreferences circuitPreferences) {
        AppPreferences wanted = disposed || circuitPreferences == windowPreferences ? null : circuitPreferences;
        if (wanted == followedPreferences) {
            return;
        }
        if (followedPreferences != null) {
            followedPreferences.removeListener(preferencesListener);
        }
        followedPreferences = wanted;
        if (followedPreferences != null) {
            followedPreferences.addListener(preferencesListener);
        }
    }

    public static String settingName(EditorSetting setting) {
        return Messages.text(switch (setting) {
            case SNAP -> "ai.setting.SNAP";
            case SNAP_TO_PARTS -> "ai.setting.SNAP_TO_PARTS";
            case SNAP_TO_GUIDES -> "ai.setting.SNAP_TO_GUIDES";
            case SNAP_TO_GRID_LINES -> "ai.setting.SNAP_TO_GRID_LINES";
            case SHOW_GUIDES -> "ai.setting.SHOW_GUIDES";
            case SHOW_BUS_VALUES -> "ai.setting.SHOW_BUS_VALUES";
            case GRID_STYLE -> "ai.setting.GRID_STYLE";
            case GRID_SPACING -> "ai.setting.GRID_SPACING";
            case WIRES_FOLLOW -> "ai.setting.WIRES_FOLLOW";
        });
    }

    /** Yes / no, the grid style's name, or the spacing as it is. */
    public static String valueText(String value) {
        return switch (value) {
            case "true" -> Messages.text("ai.setting.value.true");
            case "false" -> Messages.text("ai.setting.value.false");
            case "DOTS" -> Messages.text("ai.setting.value.DOTS");
            case "LINES" -> Messages.text("ai.setting.value.LINES");
            case "NONE" -> Messages.text("ai.setting.value.NONE");
            default -> value;
        };
    }

    private void refreshProposals() {
        proposalsSection.removeAll();
        proposalLabels.clear();
        acceptButtons.clear();
        declineButtons.clear();
        List<PreferenceProposal> pending = proposals.pending();
        proposalsSection.setVisible(!pending.isEmpty());
        if (!pending.isEmpty()) {
            proposalsSection.add(new JLabel(Messages.text("ai.panel.proposals")), "span 3");
        }
        for (PreferenceProposal proposal : pending) {
            JLabel description = plainText(proposal.description());
            JButton accept = new JButton(Messages.text("ai.proposal.accept"));
            JButton decline = new JButton(Messages.text("ai.proposal.decline"));
            accept.addActionListener(event -> proposals.answer(proposal.id(), true));
            decline.addActionListener(event -> proposals.answer(proposal.id(), false));
            acceptButtons.put(proposal.id(), accept);
            declineButtons.put(proposal.id(), decline);
            proposalsSection.add(description);
            proposalsSection.add(accept);
            proposalsSection.add(decline);
            proposalLabels.add(description);
            if (!proposal.reason().isBlank()) {
                JLabel reason = plainText(proposal.reason());
                reason.setForeground(null);
                proposalsSection.add(reason, "span 3, gapleft 16");
                proposalLabels.add(reason);
            }
        }
        revalidate();
        repaint();
    }

    /** A label that never renders its text as HTML: the AI's words are data. */
    public static JLabel plainText(String text) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        return label;
    }

    AiStateDot stateDot() {
        return header.stateDot();
    }

    JLabel titleLabel() {
        return header.titleLabel();
    }

    JLabel stateLabel() {
        return header.stateLabel();
    }

    JLabel detailsLabel() {
        return header.detailsLabel();
    }

    JButton toggleButton() {
        return header.toggleButton();
    }

    JButton restartButton() {
        return header.restartButton();
    }

    JLabel helpLabel() {
        return helpLabel;
    }

    JButton nextFreePortButton() {
        return nextFreePortButton;
    }

    JLabel notice() {
        return notice;
    }

    JLabel portNote() {
        return portNote;
    }

    JPanel tasksSection() {
        return tasksSection;
    }

    List<String> taskTexts() {
        return taskLabels.stream().map(JLabel::getText).toList();
    }

    JButton stopButton(String taskId) {
        return stopButtons.get(taskId);
    }

    JButton undoButton(String taskId) {
        return undoButtons.get(taskId);
    }

    JToggleButton configurationHeader() {
        return configurationHeader;
    }

    JPanel configurationSection() {
        return configurationSection;
    }

    JLabel portLabel() {
        return portLabel;
    }

    JButton copyButton() {
        return copyButton;
    }

    JLabel dataNote() {
        return dataNote;
    }

    JPanel settingsSection() {
        return settingsSection;
    }

    JToggleButton settingsHeader() {
        return settingsSection.header();
    }

    List<String> settingTexts() {
        return settingsSection.texts();
    }

    JButton keepSettingsButton() {
        return keepSettingsButton;
    }

    JButton dropSettingsButton() {
        return dropSettingsButton;
    }

    JPanel proposalsSection() {
        return proposalsSection;
    }

    List<JLabel> proposalLabels() {
        return List.copyOf(proposalLabels);
    }

    List<String> proposalTexts() {
        return proposalLabels.stream().map(JLabel::getText).toList();
    }

    JButton acceptButton(String proposalId) {
        return acceptButtons.get(proposalId);
    }

    JButton declineButton(String proposalId) {
        return declineButtons.get(proposalId);
    }
}
