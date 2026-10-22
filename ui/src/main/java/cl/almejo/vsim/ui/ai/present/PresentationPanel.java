package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.ui.ai.AiPanel;
import cl.almejo.vsim.ui.ai.SettingsOverride;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The floating «Presentar» panel of one window (spec §5, mockups B + C, «ai-step-panel-v2»): the header «IA · <task>»
 * with «Etapa n / m», the «De corrido» | «Paso a paso» switch, the stages as {@link StageCard}s, «Siguiente ▸»,
 * «Hasta fin de etapa», «Detener», «Deshacer etapa» and, at the end, «Dejar solo el resultado»; the folded «Ajustes
 * que tocó la IA (n)» and the pause between steps in its menu. It shows a {@link PresentedTask} and asks the
 * {@link PresentationControls}; the pace and the pause are the window's preferences. Enter is «Siguiente» while it
 * has the focus. EDT only.
 */
final class PresentationPanel extends JPanel {

    /** The pauses the menu offers, within {@link PresentationSettings#MINIMUM_PAUSE} … {@code MAXIMUM_PAUSE}. */
    static final List<Integer> PAUSE_CHOICES_MILLIS = List.of(300, 500, 1000, 2000, 3000, 5000);
    private static final String NEXT_KEY = "ai.present.next";
    private static final String FOLD_MARK = "▾";
    private static final String UNFOLD_MARK = "▸";

    private final AppPreferences windowPreferences;
    private final PresentationControls controls;
    private final Optional<SettingsOverride> override;
    private final StageCard.Line header = new StageCard.Line(" ", StageCard.Tone.NORMAL, StageCard.Style.BOLD);
    private final JLabel stageLabel = new JLabel(" ");
    private final JButton menuButton = new JButton("⋯");
    private final JButton foldButton = new JButton(FOLD_MARK);
    private final JButton closeButton = new JButton("×");
    private final JPanel body = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 6", "[grow, fill]"));
    private final JLabel notice = AiPanel.plainText(" ");
    private final JToggleButton continuousButton = new JToggleButton(Messages.text("ai.present.continuous"));
    private final JToggleButton stepByStepButton = new JToggleButton(Messages.text("ai.present.stepByStep"));
    private final JPanel cardsPanel = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 4", "[grow, fill]"));
    private final JButton nextButton = new JButton(Messages.text("ai.present.next"));
    private final JButton toStageEndButton = new JButton(Messages.text("ai.present.toStageEnd"));
    private final JButton stopButton = new JButton(Messages.text("ai.present.stop"));
    private final JButton undoStageButton = new JButton(Messages.text("ai.present.undoStage"));
    private final JButton keepOnlyResultButton = new JButton(Messages.text("ai.present.keepOnlyResult"));
    private final JPanel settingsSection = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 2", "[grow, fill]"));
    private final JToggleButton settingsHeader = new JToggleButton();
    private final JPanel settingsList = new JPanel(new MigLayout("insets 0 12 0 0, wrap 1, gap 0 2"));
    private final JPopupMenu menu = new JPopupMenu();
    private final List<JMenuItem> pauseItems = new ArrayList<>();
    private final List<StageCard> cards = new ArrayList<>();
    private final List<JLabel> settingLabels = new ArrayList<>();
    private final Runnable preferencesListener = this::followPreferences;
    private Runnable sizeChanged = () -> {
    };
    private PresentedTask shown;
    private boolean aiSwitched;
    private boolean folded;

    /** @param close what «×» does once the task ended (the window hides the panel) */
    PresentationPanel(AppPreferences windowPreferences, PresentationControls controls, Runnable close) {
        super(new MigLayout("insets 8, wrap 1, gap 0 6, width 320:340:420", "[grow, fill]"));
        this.windowPreferences = Objects.requireNonNull(windowPreferences, "windowPreferences");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.override = SettingsOverride.of(windowPreferences);
        Objects.requireNonNull(close, "close");
        setBorder(new StageCard.Outline(1));

        buildHeader(close);
        buildBody();
        buildMenu();
        bindEnterToNext();
        windowPreferences.addListener(preferencesListener);
        followPreferences();
    }

    private void buildHeader(Runnable close) {
        menuButton.setToolTipText(Messages.text("ai.present.menu"));
        foldButton.setToolTipText(Messages.text("ai.present.fold"));
        closeButton.setToolTipText(Messages.text("ai.present.close"));
        menuButton.addActionListener(event -> menu.show(menuButton, 0, menuButton.getHeight()));
        foldButton.addActionListener(event -> setFolded(!folded));
        closeButton.addActionListener(event -> close.run());
        add(header, "split 5, growx, wmin 0");
        add(stageLabel, "growx 0");
        add(menuButton, "growx 0");
        add(foldButton, "growx 0");
        add(closeButton, "growx 0, hidemode 3");
    }

    private void buildBody() {
        body.setOpaque(false);
        cardsPanel.setOpaque(false);
        settingsSection.setOpaque(false);
        settingsList.setOpaque(false);
        ButtonGroup pace = new ButtonGroup();
        pace.add(continuousButton);
        pace.add(stepByStepButton);
        continuousButton.addActionListener(event -> windowPreferences.setAiPace(PresentationPace.CONTINUOUS));
        stepByStepButton.addActionListener(event -> windowPreferences.setAiPace(PresentationPace.STEP_BY_STEP));
        nextButton.addActionListener(event -> ifShown(task -> controls.next(task.task().taskId())));
        toStageEndButton.addActionListener(event -> ifShown(task -> controls.toStageEnd(task.task().taskId())));
        stopButton.addActionListener(event -> ifShown(task -> controls.stop(task.task().taskId())));
        undoStageButton.addActionListener(event -> ifShown(task -> task.stageToUndo()
                .ifPresent(stage -> controls.undoStage(task.task().taskId(), stage))));
        keepOnlyResultButton.addActionListener(event -> ifShown(task -> controls.keepOnlyResult(
                task.task().taskId())));
        settingsHeader.addActionListener(event -> {
            settingsList.setVisible(settingsHeader.isSelected());
            relayout();
        });

        body.add(notice, "hidemode 3");
        body.add(continuousButton, "split 2, sizegroup pace, growx");
        body.add(stepByStepButton, "sizegroup pace, growx");
        body.add(cardsPanel);
        body.add(nextButton, "split 2, sizegroup actions, growx");
        body.add(toStageEndButton, "sizegroup actions, growx");
        body.add(stopButton, "split 2, sizegroup actions, growx");
        body.add(undoStageButton, "sizegroup actions, growx");
        body.add(keepOnlyResultButton, "hidemode 3");
        settingsList.setVisible(false);
        settingsSection.add(settingsHeader, "growx 0");
        settingsSection.add(settingsList, "hidemode 3");
        body.add(settingsSection, "hidemode 3");
        add(body, "hidemode 3");
    }

    private void buildMenu() {
        JMenuItem title = new JMenuItem(Messages.text("ai.present.pause"));
        title.setEnabled(false);
        menu.add(title);
        ButtonGroup pauses = new ButtonGroup();
        for (int millis : PAUSE_CHOICES_MILLIS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    Messages.format("ai.present.pause.seconds", millis / 1000.0));
            item.addActionListener(event -> windowPreferences.setAiPauseMillis(millis));
            pauses.add(item);
            pauseItems.add(item);
            menu.add(item);
        }
    }

    private void bindEnterToNext() {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), NEXT_KEY);
        getActionMap().put(NEXT_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (nextButton.isEnabled()) {
                    nextButton.doClick();
                }
            }
        });
    }

    /**
     * Shows {@code task} as it is now.
     *
     * @param aiSwitchedToPresenting whether to say «La IA pasó a Presentar» (when no discard has more to say)
     */
    void show(PresentedTask task, boolean aiSwitchedToPresenting) {
        this.shown = Objects.requireNonNull(task, "task");
        this.aiSwitched = aiSwitchedToPresenting;
        header.setText(Messages.format("ai.present.header", task.task().title()));
        stageLabel.setText(stageText(task));
        showNotice(task);
        showCards(task);
        refreshButtons();
        relayout();
    }

    private static String stageText(PresentedTask task) {
        int count = task.stages().size();
        if (count == 0) {
            return "";
        }
        int stage = task.isEnded() ? count : Math.min(task.currentStage() + 1, count);
        return Messages.format("ai.present.stageOf", Integer.toString(stage), Integer.toString(count));
    }

    private void showNotice(PresentedTask task) {
        Optional<String> text = task.lastDiscard().map(why -> Messages.text(why == StepStatus.STALE
                ? "ai.present.stale" : "ai.present.discarded"));
        if (text.isEmpty() && aiSwitched) {
            text = Optional.of(Messages.text("ai.present.aiSwitched"));
        }
        if (text.isEmpty() && task.isEnded()) {
            text = Optional.of(Messages.text("ai.present.ended"));
        }
        notice.setText(text.orElse(" "));
        notice.setVisible(text.isPresent());
    }

    private void showCards(PresentedTask task) {
        cardsPanel.removeAll();
        cards.clear();
        for (int stageIndex = 0; stageIndex < task.stages().size(); stageIndex++) {
            StageCard card = new StageCard(task, stageIndex);
            cards.add(card);
            cardsPanel.add(card);
        }
    }

    private void refreshButtons() {
        boolean running = shown != null && !shown.isEnded();
        boolean stepByStep = windowPreferences.aiPace() == PresentationPace.STEP_BY_STEP;
        nextButton.setEnabled(running && shown.pending().isPresent());
        toStageEndButton.setEnabled(running && stepByStep);
        stopButton.setEnabled(running);
        undoStageButton.setEnabled(shown != null && shown.stageToUndo().isPresent());
        boolean ended = shown != null && shown.isEnded();
        keepOnlyResultButton.setVisible(ended && shown.stages().size() > 1);
        closeButton.setVisible(ended);
    }

    private void followPreferences() {
        PresentationPace pace = windowPreferences.aiPace();
        continuousButton.setSelected(pace == PresentationPace.CONTINUOUS);
        stepByStepButton.setSelected(pace == PresentationPace.STEP_BY_STEP);
        int pause = windowPreferences.aiPauseMillis();
        for (int index = 0; index < pauseItems.size(); index++) {
            pauseItems.get(index).setSelected(PAUSE_CHOICES_MILLIS.get(index) == pause);
        }
        refreshSettings();
        refreshButtons();
        relayout();
    }

    private void refreshSettings() {
        List<SettingsOverride.Change> changes = override.map(SettingsOverride::changes).orElse(List.of());
        settingsSection.setVisible(!changes.isEmpty());
        settingsHeader.setText(Messages.format("ai.panel.settings", Integer.toString(changes.size())));
        settingsList.removeAll();
        settingLabels.clear();
        for (SettingsOverride.Change change : changes) {
            JLabel label = new JLabel(Messages.format("ai.setting.change", AiPanel.settingName(change.setting()),
                    AiPanel.valueText(change.value())));
            settingLabels.add(label);
            settingsList.add(label);
        }
    }

    private void setFolded(boolean fold) {
        folded = fold;
        body.setVisible(!fold);
        foldButton.setText(fold ? UNFOLD_MARK : FOLD_MARK);
        foldButton.setToolTipText(Messages.text(fold ? "ai.present.unfold" : "ai.present.fold"));
        relayout();
    }

    private void ifShown(Consumer<PresentedTask> action) {
        if (shown != null) {
            action.accept(shown);
        }
    }

    private void relayout() {
        revalidate();
        repaint();
        sizeChanged.run();
    }

    /** {@code listener} runs whenever the panel's size may have changed (its window places it again). */
    void whenSizeChanges(Runnable listener) {
        sizeChanged = Objects.requireNonNull(listener, "listener");
    }

    /** The theme's panel background, read when painted (the theme changes live). */
    @Override
    public Color getBackground() {
        Color background = UIManager.getColor("Panel.background");
        return background != null ? background : super.getBackground();
    }

    /** Stops following the window's preferences: the panel goes with its window. */
    void dispose() {
        windowPreferences.removeListener(preferencesListener);
    }

    String headerText() {
        return header.getText();
    }

    String stageText() {
        return stageLabel.getText();
    }

    JLabel header() {
        return header;
    }

    JLabel notice() {
        return notice;
    }

    JPanel body() {
        return body;
    }

    List<StageCard> cards() {
        return List.copyOf(cards);
    }

    JToggleButton continuousButton() {
        return continuousButton;
    }

    JToggleButton stepByStepButton() {
        return stepByStepButton;
    }

    JButton nextButton() {
        return nextButton;
    }

    JButton toStageEndButton() {
        return toStageEndButton;
    }

    JButton stopButton() {
        return stopButton;
    }

    JButton undoStageButton() {
        return undoStageButton;
    }

    JButton keepOnlyResultButton() {
        return keepOnlyResultButton;
    }

    JButton closeButton() {
        return closeButton;
    }

    JButton foldButton() {
        return foldButton;
    }

    List<JMenuItem> pauseItems() {
        return List.copyOf(pauseItems);
    }

    JPanel settingsSection() {
        return settingsSection;
    }

    JToggleButton settingsHeader() {
        return settingsHeader;
    }

    JPanel settingsList() {
        return settingsList;
    }

    List<String> settingTexts() {
        return settingLabels.stream().map(JLabel::getText).toList();
    }
}
