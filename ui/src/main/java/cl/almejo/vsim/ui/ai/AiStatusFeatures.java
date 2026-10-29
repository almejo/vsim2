package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.TaskStopReason;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.EveryPane;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.Color;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The AI in one window's status line (spec §4, §4.1, §8): «IA trabajando: <task>…» with «Detener» and «Deshacer lo
 * que hizo la IA» while a task runs on this window's circuit (the view stays still meanwhile); when it ends, what it
 * changed glows briefly, the active pane travels there if it is not all on screen (smoothly, or a jump with reduced
 * motion; no move when it already shows), the window's «Ajustes que tocó la IA» go back to the saved ones, and «Ver
 * lo que hizo» (travels there again) and «Deshacer lo que hizo la IA» stay until closed.
 * Also says when the AI proposes a preference. EDT only.
 */
final class AiStatusFeatures {

    /** How many of the AI's last lines the text's tooltip keeps (IA.3). */
    static final int NARRATIONS_KEPT = 5;

    /** SILENT: VSim stopped the task because the AI stopped answering while a step waited (IA.3). */
    private enum Phase { RUNNING, ENDED, STOPPED, SILENT }

    private final EditorView view;
    private final AiServerService service;
    private final Function<OpenDocument, DocumentId> ids;
    private final PreferenceProposals proposals;
    private final Optional<SettingsOverride> override;
    private final JPanel strip = new JPanel(new MigLayout("insets 0, gap 6", "[][][][][]"));
    private final JLabel text = AiPanel.plainText("");
    private final JButton stopButton = AiIcons.on(new JButton(Messages.text("ai.status.stop")), AiIcons.STOP);
    private final JButton showButton = AiIcons.on(new JButton(Messages.text("ai.status.show")), AiIcons.SHOW);
    private final JButton undoButton = AiIcons.on(new JButton(Messages.text("ai.status.undo")), AiIcons.UNDO);
    private final JButton closeButton = AiIcons.on(new JButton(), AiIcons.CLOSE);
    /** «Claude está pensando…» (IA.3), muted. */
    private final JLabel thinkingLabel = new MutedLabel(Messages.text("ai.thinking"));
    /** «La IA te pregunta: …» and «Responder…» in working mode (IA.3). */
    private final JLabel questionLabel = AiPanel.plainText("");
    private final JButton answerButton = new JButton(Messages.text("ai.status.answer"));
    private final JPopupMenu answerPopup = new JPopupMenu();
    private final ThinkingWatch thinking;
    private final Runnable preferencesListener = this::refreshQuestion;
    /** The AI's question to the user, waiting; null when none. */
    private Question openQuestion;
    /** How long the AI has not asked about the open question; zero while it asks (IA.3 review I2). */
    private Duration questionSilence = Duration.ZERO;
    private QuestionCard questionCard;
    /** Whether the shown task runs. */
    private boolean running;
    private Phase phase = Phase.RUNNING;
    private final AiChangeGlowOverlay glow;
    private final Runnable removeOverlay;
    private final Runnable removeStrip;
    private final Runnable removeServiceListener;
    private final Runnable removeProposalsListener;
    private TaskInfo shown;
    /** What the last ended task changed, for «Ver lo que hizo»; empty while a task runs or after a stop. */
    private Set<ElementId> lastChanged = Set.of();
    private int pendingProposals;
    /** What the AI said during the shown task, the last {@link #NARRATIONS_KEPT} (IA.3). */
    private final Deque<String> narrations = new ArrayDeque<>();
    private boolean disposed;

    /**
     * @param ids       the id the AI knows a circuit by
     * @param nanoClock the clock of the glow's fade
     */
    AiStatusFeatures(EditorView view, AiServerService service, Function<OpenDocument, DocumentId> ids,
                     PreferenceProposals proposals, LongSupplier nanoClock) {
        this.view = Objects.requireNonNull(view, "view");
        this.service = Objects.requireNonNull(service, "service");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.proposals = Objects.requireNonNull(proposals, "proposals");
        this.override = SettingsOverride.of(view.services().preferences());
        this.glow = new AiChangeGlowOverlay(() -> view.canvas().scheme(), view.openDocument().registry(),
                EveryPane.repaint(view), nanoClock);

        closeButton.setToolTipText(Messages.text("ai.status.close"));
        stopButton.addActionListener(event -> {
            if (shown != null) {
                service.stopTask(shown.taskId());
            }
        });
        undoButton.addActionListener(event -> undo());
        showButton.addActionListener(event -> changedBounds().ifPresent(bounds -> view.canvas().travelTo(bounds)));
        closeButton.addActionListener(event -> hide());
        this.thinking = new ThinkingWatch(nanoClock, this::quiet, this::refreshThinking);
        answerButton.addActionListener(event -> {
            if (answerButton.isShowing()) {
                answerPopup.show(answerButton, 0, -answerPopup.getPreferredSize().height);
            }
        });
        thinkingLabel.setVisible(false);
        questionLabel.setVisible(false);
        answerButton.setVisible(false);
        strip.add(text);
        strip.add(thinkingLabel, "hidemode 3");
        strip.add(questionLabel, "hidemode 3, wmax 360");
        strip.add(answerButton, "hidemode 3");
        strip.add(stopButton, "hidemode 3");
        strip.add(showButton, "hidemode 3");
        strip.add(undoButton, "hidemode 3");
        strip.add(closeButton, "hidemode 3");
        strip.setOpaque(false);
        strip.setVisible(false);
        this.removeStrip = view.statusBar().addActivity(strip);

        this.removeOverlay = EveryPane.addOverlay(view, glow);
        this.removeServiceListener = service.addListener(new AiServerService.Listener() {
            @Override
            public void taskStarted(TaskInfo task) {
                if (isThisCircuit(task)) {
                    show(task, Phase.RUNNING);
                }
            }

            @Override
            public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
                if (isThisCircuit(task)) {
                    lastChanged = changedElementIds.stream().map(ElementId::new).collect(Collectors.toSet());
                    finished(task, Phase.ENDED);
                    glow.show(lastChanged);
                    showWhatChanged();
                }
            }

            @Override
            public void taskStopped(TaskInfo task, TaskStopReason reason) {
                if (isThisCircuit(task)) {
                    lastChanged = Set.of();
                    finished(task, reason == TaskStopReason.AI_SILENT ? Phase.SILENT : Phase.STOPPED);
                }
            }

            @Override
            public void narrated(Narration narration) {
                if (shown != null && shown.taskId().equals(narration.taskId())) {
                    thinking.heard();
                    said(narration.text());
                }
            }

            @Override
            public void requestServed() {
                thinking.heard();
            }

            @Override
            public void questionAsked(Question question) {
                if (shown != null && shown.taskId().equals(question.taskId()) && running) {
                    thinking.heard();
                    asked(question);
                }
            }

            @Override
            public void questionClosed(String taskId, String questionId, boolean answered) {
                if (openQuestion != null && openQuestion.questionId().equals(questionId)) {
                    thinking.heard();
                    asked(null);
                }
            }

            @Override
            public void questionUnattended(String taskId, String questionId, Duration silentFor) {
                if (openQuestion != null && openQuestion.questionId().equals(questionId)) {
                    questionSilence = silentFor;
                    if (questionCard != null) {
                        questionCard.unattendedFor(silentFor);
                    }
                    showStatusText();
                }
            }
        });
        this.pendingProposals = proposals.pending().size();
        this.removeProposalsListener = proposals.addListener(this::followProposals);
        view.services().preferences().addListener(preferencesListener);
    }

    private boolean isThisCircuit(TaskInfo task) {
        return task.document().equals(ids.apply(view.openDocument()));
    }

    /** The task's settings go back to the saved ones (spec §7); the strip keeps «Deshacer» until closed. */
    private void finished(TaskInfo task, Phase phase) {
        override.ifPresent(SettingsOverride::drop);
        show(task, phase);
    }

    /** The active pane travels to what the task changed unless all of it is on screen already. */
    private void showWhatChanged() {
        CircuitCanvas canvas = view.canvas();
        changedBounds().filter(bounds -> !canvas.shows(bounds)).ifPresent(canvas::travelTo);
    }

    /** Where the last ended task's changes are now (those still in the circuit). */
    private Optional<Bounds> changedBounds() {
        List<DocumentElement> elements = lastChanged.stream()
                .flatMap(id -> view.openDocument().document().element(id).stream())
                .toList();
        return DrawnBounds.of(elements, view.openDocument().registry());
    }

    private void show(TaskInfo task, Phase phase) {
        if (shown == null || !shown.taskId().equals(task.taskId())) {
            narrations.clear();
        }
        shown = task;
        running = phase == Phase.RUNNING;
        if (running) {
            thinking.start();
        } else {
            thinking.stop();
            asked(null);
        }
        if (phase == Phase.RUNNING) {
            lastChanged = Set.of();
        }
        showNarrations();
        this.phase = phase;
        showStatusText();
        stopButton.setVisible(phase == Phase.RUNNING);
        showButton.setVisible(phase == Phase.ENDED && changedBounds().isPresent());
        undoButton.setVisible(true);
        closeButton.setVisible(phase != Phase.RUNNING);
        strip.setVisible(true);
        relayout();
    }

    /**
     * The AI said {@code line} (IA.3): in working mode it gets no chrome, only the text's tooltip, with the last
     * few lines («Presentar» shows them as subtitles too).
     */
    private void said(String line) {
        narrations.addLast(line);
        while (narrations.size() > NARRATIONS_KEPT) {
            narrations.removeFirst();
        }
        showNarrations();
    }

    /** The tooltip: the AI's lines as plain text, one per row (escaped: the AI's words are never HTML). */
    private void showNarrations() {
        if (narrations.isEmpty()) {
            text.setToolTipText(null);
            return;
        }
        StringBuilder tooltip = new StringBuilder("<html>").append(escaped(Messages.text("ai.status.narrations")));
        for (String line : narrations) {
            tooltip.append("<br>• ").append(escaped(line));
        }
        text.setToolTipText(tooltip.append("</html>").toString());
    }

    private static String escaped(String plain) {
        return plain.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * «IA trabajando: …», or, while its question waits and the AI stopped asking for the answer, that it waits for the
     * answer in Claude Code (IA.3 review I2: not «working» as if it were active).
     */
    private void showStatusText() {
        if (shown == null) {
            return;
        }
        boolean waitingInChat = phase == Phase.RUNNING && openQuestion != null && !questionSilence.isZero();
        text.setText(Messages.format(waitingInChat ? "ai.status.waitingInChat" : switch (phase) {
            case RUNNING -> "ai.status.working";
            case ENDED -> "ai.status.ended";
            case STOPPED -> "ai.status.stopped";
            case SILENT -> "ai.status.silent";
        }, shown.title()));
    }

    /** The AI asked {@code question} (null: it is gone); shown in working mode only, «Presentar» has its panel. */
    private void asked(Question question) {
        openQuestion = question;
        questionSilence = Duration.ZERO;
        showStatusText();
        answerPopup.setVisible(false);
        answerPopup.removeAll();
        questionCard = null;
        if (question != null) {
            questionCard = new QuestionCard(question, answer -> {
                answerPopup.setVisible(false);
                service.answerQuestion(question.questionId(), answer);
            });
            answerPopup.add(questionCard);
        }
        refreshQuestion();
        thinking.tick();
    }

    private boolean presenting() {
        return view.services().preferences().aiPresenting();
    }

    private void refreshQuestion() {
        boolean shownHere = openQuestion != null && !presenting();
        questionLabel.setText(openQuestion == null ? "" : Messages.format("ai.status.question", openQuestion.text()));
        questionLabel.setToolTipText(openQuestion == null ? null : openQuestion.text());
        questionLabel.setVisible(shownHere);
        answerButton.setVisible(shownHere);
        refreshThinking();
    }

    /** «Claude está pensando…» may show: the task runs and no question waits for the user. */
    private boolean quiet() {
        return running && openQuestion == null;
    }

    private void refreshThinking() {
        thinkingLabel.setVisible(thinking.showing() && !presenting());
        relayout();
    }

    private void hide() {
        narrations.clear();
        text.setToolTipText(null);
        thinking.stop();
        asked(null);
        running = false;
        shown = null;
        lastChanged = Set.of();
        strip.setVisible(false);
        relayout();
    }

    private void undo() {
        if (shown == null) {
            return;
        }
        TaskInfo task = shown;
        service.undoTask(task.taskId(), outcome -> {
            if (disposed) {
                return;
            }
            view.statusBar().showNotice(AiUndoNotices.of(outcome));
            if (shown == task && !service.runningTasks().contains(task)) {
                hide();
            }
        });
    }

    private void followProposals() {
        int pending = proposals.pending().size();
        if (pending > pendingProposals) {
            view.statusBar().showNotice(Messages.text("ai.proposal.arrived"));
        }
        pendingProposals = pending;
    }

    private void relayout() {
        view.statusBar().revalidate();
        view.statusBar().repaint();
    }

    void dispose() {
        disposed = true;
        thinking.dispose();
        answerPopup.setVisible(false);
        view.services().preferences().removeListener(preferencesListener);
        removeServiceListener.run();
        removeProposalsListener.run();
        removeOverlay.run();
        glow.dispose();
        removeStrip.run();
        relayout();
    }

    JPanel strip() {
        return strip;
    }

    JLabel text() {
        return text;
    }

    List<String> narrations() {
        return List.copyOf(narrations);
    }

    JLabel thinkingLabel() {
        return thinkingLabel;
    }

    JLabel questionLabel() {
        return questionLabel;
    }

    JButton answerButton() {
        return answerButton;
    }

    Optional<QuestionCard> questionCard() {
        return Optional.ofNullable(questionCard);
    }

    ThinkingWatch thinking() {
        return thinking;
    }

    /** A plain-text label in the theme's muted colour, read when painted. */
    private static final class MutedLabel extends JLabel {

        MutedLabel(String text) {
            super(text);
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Color getForeground() {
            return AiColors.muted();
        }
    }

    JButton stopButton() {
        return stopButton;
    }

    JButton showButton() {
        return showButton;
    }

    JButton undoButton() {
        return undoButton;
    }

    JButton closeButton() {
        return closeButton;
    }

    AiChangeGlowOverlay glow() {
        return glow;
    }
}
