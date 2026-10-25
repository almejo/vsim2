package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.ui.ai.AiIcons;
import cl.almejo.vsim.ui.ai.AiPanel;
import cl.almejo.vsim.ui.ai.QuestionCard;
import cl.almejo.vsim.ui.ai.SettingsOverride;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.SegmentedSwitch;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The floating «Presentar» panel of one window (spec §5, mockups B + C, «ai-step-panel-v2»): a compact card with the
 * header on one row («⋮⋮» grip, «IA · <task>», «Etapa n / m», ⋯, fold and, at the end, ×), the «De corrido» | «Paso a
 * paso» segmented switch with a line saying what the pace does (and «Siguiente paso en …» while a step waits for its
 * pause), the stages as {@link StageCard}s (scrolling when the pane is short), «Siguiente ▸», «Hasta fin de etapa»,
 * «Detener», «Deshacer etapa» and, at the end, only «Deshacer etapa», «Dejar solo el resultado» and ×; the folded
 * «Ajustes que tocó la IA (n)»; the pause and «Mover a ▸» in its menu. Long texts end in «…» with the whole text in
 * the tooltip, never cut by the edge. It shows a {@link PresentedTask} and asks the {@link PresentationControls}; the
 * pace and the pause are the window's preferences. Enter is «Siguiente» while it has the focus. EDT only.
 */
final class PresentationPanel extends JPanel {

    /** The card's width when the pane has room for it. */
    static final int PREFERRED_WIDTH = 340;
    /** The stage cards keep at least this height, scrolling, when the pane is short. */
    static final int MINIMUM_CARDS_HEIGHT = 48;
    /** The pauses the menu offers, within {@link PresentationSettings#MINIMUM_PAUSE} … {@code MAXIMUM_PAUSE}. */
    static final List<Integer> PAUSE_CHOICES_MILLIS = List.of(300, 500, 1000, 2000, 3000, 5000);
    private static final String NEXT_KEY = "ai.present.next";
    private static final String FOLD_MARK = "▾";
    private static final String UNFOLD_MARK = "▸";
    private static final String GRIP_MARK = "⋮⋮";

    private final AppPreferences windowPreferences;
    private final PresentationControls controls;
    private final Optional<SettingsOverride> override;
    private final JPanel headerBar = new JPanel(new BorderLayout(6, 0));
    private final JLabel grip = new JLabel(GRIP_MARK);
    private final StageCard.Line header = new StageCard.Line(" ", StageCard.Tone.NORMAL, StageCard.Style.BOLD);
    private final JLabel stageLabel = new JLabel(" ");
    private final JButton menuButton = headerButton("⋯");
    private final JButton foldButton = headerButton(FOLD_MARK);
    private final JButton closeButton = headerButton("×");
    /** «Me detuve: falló la comprobación de …», under the header so it shows folded too (IA.3 «Speed»). */
    private final StageCard.Line heldNotice = new StageCard.Line(" ", StageCard.Tone.ACCENT, StageCard.Style.BOLD);
    /** «Claude está pensando…» (IA.3), muted, under the header so it shows folded too. */
    private final StageCard.Line thinkingHint = new StageCard.Line(" ", StageCard.Tone.MUTED, StageCard.Style.PLAIN);
    /** The AI's question (IA.3), under the header so it shows folded too; kept while it is the same question. */
    private QuestionCard questionCard;
    private final StackLayout bodyLayout = new StackLayout(6);
    private final JPanel body = new JPanel(bodyLayout);
    private final StageCard.Line notice = new StageCard.Line(" ", StageCard.Tone.NORMAL, StageCard.Style.PLAIN);
    private final JToggleButton continuousButton = new SegmentedSwitch.Segment(Messages.text("ai.present.continuous"));
    private final JToggleButton stepByStepButton = new SegmentedSwitch.Segment(Messages.text("ai.present.stepByStep"));
    private final SegmentedSwitch paceSwitch = new SegmentedSwitch(continuousButton, stepByStepButton);
    private final StageCard.Line paceHint = new StageCard.Line(" ", StageCard.Tone.MUTED, StageCard.Style.PLAIN);
    private final CardsPanel cardsPanel = new CardsPanel();
    private final JScrollPane cardsScroll = new JScrollPane(cardsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final JButton nextButton = new JButton(Messages.text("ai.present.next"));
    private final JButton toStageEndButton = new JButton(Messages.text("ai.present.toStageEnd"));
    private final JButton stopButton = AiIcons.on(new JButton(Messages.text("ai.present.stop")), AiIcons.STOP);
    private final JButton undoStageButton = new JButton(Messages.text("ai.present.undoStage"));
    private final JButton keepOnlyResultButton = new JButton(Messages.text("ai.present.keepOnlyResult"));
    private final ButtonRow stepRow = new ButtonRow(nextButton, toStageEndButton);
    private final ButtonRow stageRow = new ButtonRow(stopButton, undoStageButton, keepOnlyResultButton);
    private final JPanel settingsSection = new JPanel(new MigLayout("insets 0, wrap 1, gap 0 2, hidemode 3",
            "[grow, fill]"));
    private final JToggleButton settingsHeader = new JToggleButton();
    private final JPanel settingsList = new JPanel(new MigLayout("insets 0 12 0 0, wrap 1, gap 0 2"));
    private final JPopupMenu menu = new JPopupMenu();
    private final List<JMenuItem> pauseItems = new ArrayList<>();
    private final List<JMenuItem> moveItems = new ArrayList<>();
    private final List<StageCard> cards = new ArrayList<>();
    private final List<JLabel> settingLabels = new ArrayList<>();
    private final Runnable preferencesListener = this::followPreferences;
    private Runnable sizeChanged = () -> {
    };
    private Consumer<FloatingPlacement.Corner> moveTo = corner -> {
    };
    private PresentedTask shown;
    private boolean aiSwitched;
    private boolean folded;

    /** @param close what «×» does once the task ended (the window hides the panel) */
    PresentationPanel(AppPreferences windowPreferences, PresentationControls controls, Runnable close) {
        super(new StackLayout(6));
        this.windowPreferences = Objects.requireNonNull(windowPreferences, "windowPreferences");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.override = SettingsOverride.of(windowPreferences);
        Objects.requireNonNull(close, "close");
        setBorder(BorderFactory.createCompoundBorder(new StageCard.Outline(1),
                BorderFactory.createEmptyBorder(5, 7, 7, 7)));

        buildHeader(close);
        buildBody();
        buildMenu();
        bindEnterToNext();
        windowPreferences.addListener(preferencesListener);
        followPreferences();
    }

    private static JButton headerButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(0, 4, 0, 4));
        button.setFocusable(true);
        return button;
    }

    private void buildHeader(Runnable close) {
        menuButton.setToolTipText(Messages.text("ai.present.menu"));
        foldButton.setToolTipText(Messages.text("ai.present.fold"));
        closeButton.setToolTipText(Messages.text("ai.present.close"));
        menuButton.addActionListener(event -> menu.show(menuButton, 0, menuButton.getHeight()));
        foldButton.addActionListener(event -> setFolded(!folded));
        closeButton.addActionListener(event -> close.run());
        grip.setToolTipText(Messages.text("ai.present.grip"));
        headerBar.setOpaque(false);
        stageLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        right.setOpaque(false);
        right.add(stageLabel);
        right.add(menuButton);
        right.add(foldButton);
        right.add(closeButton);
        headerBar.add(grip, BorderLayout.WEST);
        headerBar.add(header, BorderLayout.CENTER);
        headerBar.add(right, BorderLayout.EAST);
        Cursor move = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        for (JComponent handle : dragHandles()) {
            handle.setCursor(move);
        }
        add(headerBar);
        heldNotice.setVisible(false);
        add(heldNotice);
        thinkingHint.setText(Messages.text("ai.thinking"));
        thinkingHint.setVisible(false);
        add(thinkingHint);
    }

    private void buildBody() {
        body.setOpaque(false);
        cardsPanel.setOpaque(false);
        cardsScroll.setOpaque(false);
        cardsScroll.getViewport().setOpaque(false);
        cardsScroll.setBorder(BorderFactory.createEmptyBorder());
        cardsScroll.getVerticalScrollBar().setUnitIncrement(16);
        settingsSection.setOpaque(false);
        settingsList.setOpaque(false);
        continuousButton.addActionListener(event -> windowPreferences.setAiPace(PresentationPace.CONTINUOUS));
        stepByStepButton.addActionListener(event -> windowPreferences.setAiPace(PresentationPace.STEP_BY_STEP));
        nextButton.addActionListener(event -> ifShown(task -> {
            task.continued();
            controls.next(task.task().taskId());
            show(task, aiSwitched);
        }));
        toStageEndButton.addActionListener(event -> ifShown(task -> controls.toStageEnd(task.task().taskId())));
        stopButton.addActionListener(event -> ifShown(task -> controls.stop(task.task().taskId())));
        undoStageButton.addActionListener(event -> ifShown(task -> controls.undoStage(task.task().taskId(),
                outcome -> answered(task, () -> task.stageUndone(outcome)))));
        keepOnlyResultButton.addActionListener(event -> ifShown(task -> controls.keepOnlyResult(
                task.task().taskId(), done -> answered(task, () -> task.keptOnlyResult(done)))));
        settingsHeader.addActionListener(event -> {
            settingsList.setVisible(settingsHeader.isSelected());
            relayout();
        });

        cardsScroll.setMinimumSize(new Dimension(0, MINIMUM_CARDS_HEIGHT));
        bodyLayout.setFlexible(cardsScroll);
        body.add(notice);
        body.add(paceSwitch);
        body.add(paceHint);
        body.add(cardsScroll);
        body.add(stepRow);
        body.add(stageRow);
        settingsList.setVisible(false);
        settingsSection.add(settingsHeader, "growx 0, wmin 0");
        settingsSection.add(settingsList, "wmin 0");
        body.add(settingsSection);
        add(body);
        ((StackLayout) getLayout()).setFlexible(body);
    }

    private void buildMenu() {
        JMenu pause = new JMenu(Messages.text("ai.present.pause"));
        ButtonGroup pauses = new ButtonGroup();
        for (int millis : PAUSE_CHOICES_MILLIS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    Messages.format("ai.present.pause.seconds", millis / 1000.0));
            item.addActionListener(event -> windowPreferences.setAiPauseMillis(millis));
            pauses.add(item);
            pauseItems.add(item);
            pause.add(item);
        }
        menu.add(pause);
        JMenu move = new JMenu(Messages.text("ai.present.moveTo"));
        for (FloatingPlacement.Corner corner : FloatingPlacement.Corner.values()) {
            JMenuItem item = new JMenuItem(Messages.text(corner.messageKey()));
            item.addActionListener(event -> moveTo.accept(corner));
            moveItems.add(item);
            move.add(item);
        }
        menu.add(move);
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
        if (task.isEnded() && !task.storyboardAsked()) {
            // what the end may offer: «Deshacer etapa» while a stage is left, «Dejar solo el resultado» when separate
            task.storyboardAskedNow();
            controls.storyboardState(task.task().taskId(),
                    state -> answered(task, () -> task.storyboardChanged(state)));
        }
        header.setText(Messages.format("ai.present.header", task.task().title()));
        stageLabel.setText(stageText(task));
        showNotice(task);
        showHeld(task);
        showQuestion(task);
        thinkingHint.setVisible(task.thinking());
        showCards(task);
        refreshButtons();
        relayout();
    }

    /** An answer of the controls about {@code task} (on the editor thread): shown again while it still is shown. */
    private void answered(PresentedTask task, Runnable change) {
        change.run();
        if (shown == task) {
            show(task, aiSwitched);
        }
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
        // a silent AI is only ever stopped with a ghost waiting: why it stopped matters more than the dropped ghost
        Optional<String> text = task.isEnded() && task.stoppedBecauseSilent()
                ? Optional.of(Messages.text("ai.present.silent")) : Optional.empty();
        if (text.isEmpty()) {
            text = task.lastDiscard().map(why -> Messages.text(why == StepStatus.STALE
                    ? "ai.present.stale" : "ai.present.discarded"));
        }
        if (text.isEmpty()) {
            text = task.lastUndo().map(PresentationPanel::undoNotice);
        }
        if (text.isEmpty() && task.keepRefused()) {
            text = Optional.of(Messages.text("ai.present.notSeparate"));
        }
        if (text.isEmpty() && aiSwitched) {
            text = Optional.of(Messages.text("ai.present.aiSwitched"));
        }
        if (text.isEmpty() && task.isEnded()) {
            text = Optional.of(Messages.text(task.stoppedBecauseSilent() ? "ai.present.silent"
                    : task.isStopped() ? "ai.present.stopped" : "ai.present.ended"));
        }
        notice.setText(text.orElse(" "));
        notice.setVisible(text.isPresent());
    }

    /**
     * «De corrido» stopped after a failed check: said plainly under the header, in the accent, until the AI checks
     * again and passes or the user presses «Siguiente». «Paso a paso» waits at every step anyway: nothing to say.
     */
    private void showHeld(PresentedTask task) {
        Optional<String> text = task.heldAfterCheck()
                .filter(stage -> windowPreferences.aiPace() == PresentationPace.CONTINUOUS && !task.isEnded())
                .filter(stage -> stage < task.stages().size())
                .map(stage -> Messages.format("ai.present.heldAfterCheck", task.stages().get(stage).title()));
        heldNotice.setText(text.orElse(" "));
        heldNotice.setVisible(text.isPresent());
    }

    /**
     * The AI's question as a card right under the header, with its buttons and field; the same card while it is the
     * same question (what the user is typing stays), another one for a new question, none once it is gone.
     */
    private void showQuestion(PresentedTask task) {
        Optional<Question> question = task.question();
        if (question.isPresent() && questionCard != null
                && questionCard.question().questionId().equals(question.get().questionId())) {
            questionCard.unattendedFor(task.questionSilence());
            return;
        }
        if (questionCard != null) {
            remove(questionCard);
            questionCard = null;
        }
        question.ifPresent(asked -> {
            questionCard = new QuestionCard(asked, answer -> controls.answer(asked.questionId(), answer));
            questionCard.unattendedFor(task.questionSilence());
            add(questionCard, getComponentZOrder(heldNotice) + 1);
        });
    }

    /** What «Deshacer etapa» did, in the IA.1 words where they fit. */
    static String undoNotice(StageUndoOutcome outcome) {
        String undone = Integer.toString(outcome.undone());
        return switch (outcome.stoppedAt()) {
            case USER_EDIT -> Messages.format("ai.status.undoStoppedAtUser", undone);
            case ANOTHER_STAGE -> Messages.format("ai.present.undoStoppedAtStage", undone);
            case NOTHING -> outcome.undone() == 0 ? Messages.text("ai.present.nothingToUndo")
                    : Messages.format("ai.present.stageUndone", Integer.toString(outcome.stageIndex() + 1),
                    outcome.undone());
        };
    }

    private void showCards(PresentedTask task) {
        cardsPanel.removeAll();
        cards.clear();
        for (int stageIndex = 0; stageIndex < task.stages().size(); stageIndex++) {
            StageCard card = new StageCard(task, stageIndex);
            cards.add(card);
            cardsPanel.add(card, "wmin 0");
        }
    }

    private void refreshButtons() {
        boolean running = shown != null && !shown.isEnded();
        boolean stepByStep = windowPreferences.aiPace() == PresentationPace.STEP_BY_STEP;
        nextButton.setEnabled(running && shown.pending().isPresent());
        toStageEndButton.setEnabled(running && stepByStep);
        stopButton.setEnabled(running);
        undoStageButton.setEnabled(shown != null && shown.canUndoStage());
        boolean ended = shown != null && shown.isEnded();
        // at the end only what makes sense then: undo the stage, keep only the result, close
        nextButton.setVisible(!ended);
        toStageEndButton.setVisible(!ended);
        stopButton.setVisible(!ended);
        paceSwitch.setVisible(!ended);
        paceHint.setVisible(!ended);
        keepOnlyResultButton.setVisible(ended && shown.canKeepOnlyResult());
        closeButton.setVisible(ended);
        stepRow.setVisible(stepRow.hasVisibleButton());
        stageRow.setVisible(stageRow.hasVisibleButton());
        paceHint.setText(paceHintText());
        if (shown != null) {
            showHeld(shown); // the pace may have changed
        }
    }

    private String paceHintText() {
        if (windowPreferences.aiPace() == PresentationPace.STEP_BY_STEP) {
            return Messages.text("ai.present.paceHint.stepByStep");
        }
        double seconds = windowPreferences.aiPauseMillis() / 1000.0;
        Optional<Duration> paced = shown == null ? Optional.empty() : shown.pacedFor();
        return paced.map(delay -> Messages.format("ai.present.paceHint.nextIn", delay.toMillis() / 1000.0))
                .orElse(Messages.format("ai.present.paceHint.continuous", seconds));
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
            JLabel label = new StageCard.Line(Messages.format("ai.setting.change",
                    AiPanel.settingName(change.setting()), AiPanel.valueText(change.value())), null, null);
            settingLabels.add(label);
            settingsList.add(label, "wmin 0");
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

    /** What «Mover a ▸» does (its window moves the panel to that corner of the pane). */
    void whenMovedTo(Consumer<FloatingPlacement.Corner> listener) {
        moveTo = Objects.requireNonNull(listener, "listener");
    }

    /** {@link #PREFERRED_WIDTH} wide; as tall as its contents (its window gives it less when the pane is short). */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PREFERRED_WIDTH, super.getPreferredSize().height);
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

    /** Where the user grabs the panel to move it: the header but its buttons. */
    List<JComponent> dragHandles() {
        return List.of(headerBar, grip, header, stageLabel);
    }

    String headerText() {
        return header.getText();
    }

    String stageText() {
        return stageLabel.getText();
    }

    JPanel headerBar() {
        return headerBar;
    }

    JLabel grip() {
        return grip;
    }

    StageCard.Line header() {
        return header;
    }

    JLabel stageLabel() {
        return stageLabel;
    }

    JButton menuButton() {
        return menuButton;
    }

    JLabel notice() {
        return notice;
    }

    StageCard.Line heldNotice() {
        return heldNotice;
    }

    StageCard.Line thinkingHint() {
        return thinkingHint;
    }

    Optional<QuestionCard> questionCard() {
        return Optional.ofNullable(questionCard);
    }

    JLabel paceHint() {
        return paceHint;
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

    List<JMenuItem> moveItems() {
        return List.copyOf(moveItems);
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

    /** The stage cards, as wide as the scroll pane that holds them (never wider: their texts end in «…»). */
    private static final class CardsPanel extends JPanel implements Scrollable {

        CardsPanel() {
            super(new MigLayout("insets 0, wrap 1, gap 0 4", "[grow, fill]"));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
