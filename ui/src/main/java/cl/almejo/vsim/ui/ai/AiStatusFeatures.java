package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.EveryPane;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The AI in one window's status line (spec §4, §4.1, §8): «IA trabajando: <task>…» with «Detener» and «Deshacer lo
 * que hizo la IA» while a task runs on this window's circuit; when it ends, what it changed glows briefly, the
 * window's «Ajustes que tocó la IA» go back to the saved ones and «Deshacer lo que hizo la IA» stays until closed.
 * Also says when the AI proposes a preference. EDT only.
 */
final class AiStatusFeatures {

    private enum Phase { RUNNING, ENDED, STOPPED }

    private final EditorView view;
    private final AiServerService service;
    private final Function<OpenDocument, DocumentId> ids;
    private final PreferenceProposals proposals;
    private final Optional<SettingsOverride> override;
    private final JPanel strip = new JPanel(new MigLayout("insets 0, gap 6", "[][][][]"));
    private final JLabel text = AiPanel.plainText("");
    private final JButton stopButton = new JButton(Messages.text("ai.status.stop"));
    private final JButton undoButton = new JButton(Messages.text("ai.status.undo"));
    private final JButton closeButton = new JButton("×");
    private final AiChangeGlowOverlay glow;
    private final Runnable removeOverlay;
    private final Runnable removeServiceListener;
    private final Runnable removeProposalsListener;
    private TaskInfo shown;
    private int pendingProposals;
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
        closeButton.addActionListener(event -> hide());
        strip.add(text);
        strip.add(stopButton, "hidemode 3");
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
                    finished(task, Phase.ENDED);
                    glow.show(changedElementIds.stream().map(ElementId::new).collect(Collectors.toSet()));
                }
            }

            @Override
            public void taskStopped(TaskInfo task) {
                if (isThisCircuit(task)) {
                    finished(task, Phase.STOPPED);
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

    private void show(TaskInfo task, Phase phase) {
        shown = task;
        text.setText(Messages.format(switch (phase) {
            case RUNNING -> "ai.status.working";
            case ENDED -> "ai.status.ended";
            case STOPPED -> "ai.status.stopped";
        }, task.title()));
        stopButton.setVisible(phase == Phase.RUNNING);
        undoButton.setVisible(true);
        closeButton.setVisible(phase != Phase.RUNNING);
        strip.setVisible(true);
        relayout();
    }

    private void hide() {
        shown = null;
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

    JButton stopButton() {
        return stopButton;
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
