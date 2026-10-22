package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Herramientas ▸ «IA (MCP)…» of one window (spec §2, §7, §9.1): the server's live state in words and colour with
 * «Activar» / «Desactivar», «Usar el siguiente libre» when the port is taken, «Copiar configuración para Claude Code»,
 * the data note, the folded «Ajustes que tocó la IA» of this window, and the AI's preference proposals with
 * «Aceptar» / «No». The AI's texts are shown as plain text, never as HTML. EDT only.
 */
public final class AiPanel extends JPanel {

    private final AiServerService service;
    private final Optional<SettingsOverride> override;
    private final AppPreferences windowPreferences;
    private final PreferenceProposals proposals;
    private final Consumer<String> clipboard;
    private final JLabel stateLabel = new StateLabel();
    private final JButton toggleButton = new JButton();
    private final JButton nextFreePortButton = new JButton(Messages.text("ai.panel.useNextFreePort"));
    private final JLabel notice = new JLabel(" ");
    private final JButton copyButton = new JButton(Messages.text("ai.copyConfiguration"));
    private final JLabel dataNote = new JLabel(Messages.text("ai.dataNote"));
    private final JPanel settingsSection = new JPanel(new MigLayout("insets 0, wrap 1", "[grow, fill]"));
    private final JToggleButton settingsHeader = new JToggleButton();
    private final JPanel settingsList = new JPanel(new MigLayout("insets 0 16 0 0, wrap 1, gap 2"));
    private final JButton keepSettingsButton = new JButton(Messages.text("ai.panel.settings.keep"));
    private final JButton dropSettingsButton = new JButton(Messages.text("ai.panel.settings.drop"));
    private final JPanel proposalsSection = new JPanel(new MigLayout("insets 0, wrap 3", "[grow, fill][][]"));
    private final List<JLabel> settingLabels = new ArrayList<>();
    private final List<JLabel> proposalLabels = new ArrayList<>();
    private final Map<String, JButton> acceptButtons = new HashMap<>();
    private final Map<String, JButton> declineButtons = new HashMap<>();
    private final Runnable removeServiceListener;
    private final Runnable removeProposalsListener;
    private final Runnable preferencesListener = this::refreshSettings;
    private String noticeText = " ";

    /**
     * @param windowPreferences the preferences of this panel's window: its «Ajustes que tocó la IA» are their layer
     * @param clipboard         where «Copiar configuración» puts the line
     */
    public AiPanel(AiServerService service, AppPreferences windowPreferences, PreferenceProposals proposals,
                   Consumer<String> clipboard) {
        super(new MigLayout("insets dialog, wrap 1, width 460", "[grow, fill]"));
        this.service = Objects.requireNonNull(service, "service");
        this.windowPreferences = Objects.requireNonNull(windowPreferences, "windowPreferences");
        this.override = SettingsOverride.of(windowPreferences);
        this.proposals = Objects.requireNonNull(proposals, "proposals");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");

        toggleButton.addActionListener(event -> {
            if (service.state().isListening()) {
                service.deactivate();
            } else {
                noticeText = " ";
                service.activate();
            }
        });
        nextFreePortButton.addActionListener(event -> {
            int taken = windowPreferences.aiPort();
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
        settingsHeader.addActionListener(event -> settingsList.setVisible(settingsHeader.isSelected()));
        keepSettingsButton.addActionListener(event -> override.ifPresent(SettingsOverride::keep));
        dropSettingsButton.addActionListener(event -> override.ifPresent(SettingsOverride::drop));

        add(stateLabel, "split 2, growx");
        add(toggleButton, "growx 0");
        add(nextFreePortButton, "growx 0, hidemode 3");
        add(notice);
        add(copyButton, "growx 0, gaptop 8");
        add(dataNote, "gaptop 4");
        settingsList.setVisible(false);
        settingsSection.add(settingsHeader, "growx 0");
        settingsSection.add(settingsList, "hidemode 3");
        settingsSection.add(keepSettingsButton, "split 2, growx 0");
        settingsSection.add(dropSettingsButton, "growx 0");
        add(settingsSection, "gaptop 12, hidemode 3");
        add(proposalsSection, "gaptop 12, hidemode 3");

        this.removeServiceListener = service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                refreshState();
            }
        });
        this.removeProposalsListener = proposals.addListener(this::refreshProposals);
        windowPreferences.addListener(preferencesListener);
        refreshState();
        refreshSettings();
        refreshProposals();
    }

    /** Stops following the service, the proposals and the window's preferences: the panel closed. */
    public void dispose() {
        removeServiceListener.run();
        removeProposalsListener.run();
        windowPreferences.removeListener(preferencesListener);
    }

    private void refreshState() {
        AiServerState state = service.state();
        stateLabel.setText(state.text());
        toggleButton.setText(Messages.text(state.isListening() ? "ai.panel.deactivate" : "ai.panel.activate"));
        nextFreePortButton.setVisible(state instanceof AiServerState.PortInUse);
        notice.setText(service.portChanged() ? Messages.text("ai.panel.portChanged") : noticeText);
        revalidate();
        repaint();
    }

    private void refreshSettings() {
        List<SettingsOverride.Change> changes = override.map(SettingsOverride::changes).orElse(List.of());
        settingsSection.setVisible(!changes.isEmpty());
        settingsHeader.setText(Messages.format("ai.panel.settings", Integer.toString(changes.size())));
        settingsList.removeAll();
        settingLabels.clear();
        for (SettingsOverride.Change change : changes) {
            JLabel label = new JLabel(Messages.format("ai.setting.change", settingName(change.setting()),
                    valueText(change.value())));
            settingLabels.add(label);
            settingsList.add(label);
        }
        revalidate();
        repaint();
    }

    public static String settingName(EditorSetting setting) {
        return Messages.text(switch (setting) {
            case SNAP -> "ai.setting.SNAP";
            case SNAP_TO_PARTS -> "ai.setting.SNAP_TO_PARTS";
            case SNAP_TO_GUIDES -> "ai.setting.SNAP_TO_GUIDES";
            case SNAP_TO_GRID_LINES -> "ai.setting.SNAP_TO_GRID_LINES";
            case SHOW_GUIDES -> "ai.setting.SHOW_GUIDES";
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

    /** Coloured by the state at paint time, so a theme change shows at once. */
    private final class StateLabel extends JLabel {
        @Override
        public Color getForeground() {
            return service == null ? super.getForeground() : AiColors.of(service.state());
        }
    }

    JLabel stateLabel() {
        return stateLabel;
    }

    JButton toggleButton() {
        return toggleButton;
    }

    JButton nextFreePortButton() {
        return nextFreePortButton;
    }

    JLabel notice() {
        return notice;
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
        return settingsHeader;
    }

    List<String> settingTexts() {
        return settingLabels.stream().map(JLabel::getText).toList();
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
