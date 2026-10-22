package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.TaskStopReason;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.EveryPane;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
    private final AiChangeGlowOverlay glow;
    private final Runnable removeOverlay;
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
        strip.add(text);
        strip.add(stopButton, "hidemode 3");
        strip.add(showButton, "hidemode 3");
        strip.add(undoButton, "hidemode 3");
        strip.add(closeButton, "hidemode 3");
        strip.setOpaque(false);
        strip.setVisible(false);
        StatusBar statusBar = view.statusBar();
        statusBar.add(strip, "hidemode 3");

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
                    said(narration.text());
                }
            }
        });
        this.pendingProposals = proposals.pending().size();
        this.removeProposalsListener = proposals.addListener(this::followProposals);
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
        if (phase == Phase.RUNNING) {
            lastChanged = Set.of();
        }
        showNarrations();
        text.setText(Messages.format(switch (phase) {
            case RUNNING -> "ai.status.working";
            case ENDED -> "ai.status.ended";
            case STOPPED -> "ai.status.stopped";
            case SILENT -> "ai.status.silent";
        }, task.title()));
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

    private void hide() {
        narrations.clear();
        text.setToolTipText(null);
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
            view.statusBar().showNotice(undoNotice(outcome));
            if (shown == task && !service.runningTasks().contains(task)) {
                hide();
            }
        });
    }

    private static String undoNotice(UndoOutcome outcome) {
        return Messages.format(outcome.stoppedAtUserEdit() ? "ai.status.undoStoppedAtUser" : "ai.status.undone",
                Integer.toString(outcome.undone()));
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
        removeServiceListener.run();
        removeProposalsListener.run();
        removeOverlay.run();
        glow.dispose();
        view.statusBar().remove(strip);
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
