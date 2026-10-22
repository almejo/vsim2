package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.TaskStopReason;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.ui.ai.ThinkingWatch;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * «Presentar» in one editor view (spec §4, §5): the toggle (a toolbar button at the end and a checkbox item at the
 * end of Ver, both on one action whose selected state is {@link AppPreferences#aiPresenting()}, remembered and shared
 * by every window), Ver ▸ «Ritmo de Presentar» (the pace and the pause, usable before a task) and the floating
 * {@link PresentationPanel}, shown while a task of this window's circuit runs (or
 * ended and is not closed yet) and «Presentar» is on. The events come off the editor thread and are moved to it;
 * {@link #dispose()} takes back every registration. EDT only.
 */
final class PresentationFeatures {

    private final EditorView view;
    private final AppPreferences preferences;
    private final Function<OpenDocument, DocumentId> ids;
    private final Executor editorThread;
    private final Action toggle;
    private final JMenu viewMenu;
    private final JPopupMenu.Separator menuSeparator = new JPopupMenu.Separator();
    private final JCheckBoxMenuItem menuItem;
    /** Ver ▸ «Ritmo de Presentar»: the pace and the pause, also before a task (agreed with the user). */
    private final JMenu paceMenu = new JMenu(Messages.text("action.aiPresentPace"));
    private final JRadioButtonMenuItem continuousItem = new JRadioButtonMenuItem(
            Messages.text("ai.present.continuous"));
    private final JRadioButtonMenuItem stepByStepItem = new JRadioButtonMenuItem(
            Messages.text("ai.present.stepByStep"));
    private final List<JRadioButtonMenuItem> pauseItems = new ArrayList<>();
    private final JToolBar.Separator toolbarSeparator = new JToolBar.Separator();
    private final JToggleButton toolbarButton;
    private final Runnable removeShortcut;
    private final PresentationPanel panel;
    private final FloatingPlacement placement;
    private final Runnable removePresentationListener;
    private final Runnable removeTaskListener;
    private final Runnable removeActivityListener;
    /** «Claude está pensando…» (IA.3): after a silence of the AI while nothing waits for the user. */
    private final ThinkingWatch thinking;
    private final Runnable preferencesListener = this::followPreferences;
    private final HierarchyListener hierarchyListener = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
            refreshPanel();
        }
    };
    /** The task shown (running, or ended and not closed yet); null when none. */
    private PresentedTask task;
    /** «La IA pasó a Presentar» until the next step is in. */
    private boolean aiSwitched;
    private boolean disposed;

    PresentationFeatures(EditorView view, PresentationEvents events, PresentationControls controls,
                         Function<OpenDocument, DocumentId> ids, Executor editorThread) {
        this(view, events, controls, ids, editorThread, System::nanoTime);
    }

    /** @param nanoClock the clock of «Claude está pensando…» */
    PresentationFeatures(EditorView view, PresentationEvents events, PresentationControls controls,
                         Function<OpenDocument, DocumentId> ids, Executor editorThread, LongSupplier nanoClock) {
        this.view = Objects.requireNonNull(view, "view");
        this.thinking = new ThinkingWatch(nanoClock, this::quiet, this::thinkingChanged);
        this.preferences = view.services().preferences();
        this.ids = Objects.requireNonNull(ids, "ids");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
        this.toggle = new AbstractAction(Messages.text("action.aiPresent")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setAiPresenting(!preferences.aiPresenting());
            }
        };
        Icons.install(toggle, PresentationIcons.PRESENT);

        this.viewMenu = view.menu(MenuId.VIEW);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        viewMenu.add(menuSeparator);
        viewMenu.add(menuItem);
        buildPaceMenu();
        viewMenu.add(paceMenu);
        this.toolbarButton = new JToggleButton(toggle);
        view.toolBar().add(toolbarSeparator);
        view.toolBar().add(toolbarButton);
        view.toolBar().revalidate();
        this.removeShortcut = view.registerShortcutAction(PresentationExtension.SHORTCUT_ID, toggle);

        this.panel = new PresentationPanel(preferences, controls, this::closePanel);
        this.placement = new FloatingPlacement(view, panel, panel.dragHandles());
        panel.whenSizeChanges(placement::place);
        panel.whenMovedTo(placement::moveTo);
        view.addHierarchyListener(hierarchyListener);
        preferences.addListener(preferencesListener);
        this.removePresentationListener = events.addPresentationListener(new OnEditorThread());
        this.removeTaskListener = events.addTaskListener(new TaskEvents());
        this.removeActivityListener = events.addActivityListener(() -> later(thinking::heard));
        followPreferences();
    }

    /** «Claude está pensando…» may show: the task runs and no step, pause or question waits for the user. */
    private boolean quiet() {
        return task != null && !task.isEnded() && task.pending().isEmpty() && task.pacedFor().isEmpty()
                && task.question().isEmpty();
    }

    private void thinkingChanged() {
        if (task != null) {
            task.thinking(thinking.showing());
            refreshPanel();
        }
    }

    private void buildPaceMenu() {
        ButtonGroup paces = new ButtonGroup();
        paces.add(continuousItem);
        paces.add(stepByStepItem);
        continuousItem.addActionListener(event -> preferences.setAiPace(PresentationPace.CONTINUOUS));
        stepByStepItem.addActionListener(event -> preferences.setAiPace(PresentationPace.STEP_BY_STEP));
        paceMenu.add(continuousItem);
        paceMenu.add(stepByStepItem);
        paceMenu.addSeparator();
        JMenu pause = new JMenu(Messages.text("ai.present.pause"));
        ButtonGroup pauses = new ButtonGroup();
        for (int millis : PresentationPanel.PAUSE_CHOICES_MILLIS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    Messages.format("ai.present.pause.seconds", millis / 1000.0));
            item.addActionListener(event -> preferences.setAiPauseMillis(millis));
            pauses.add(item);
            pauseItems.add(item);
            pause.add(item);
        }
        paceMenu.add(pause);
    }

    private void followPace() {
        PresentationPace pace = preferences.aiPace();
        continuousItem.setSelected(pace == PresentationPace.CONTINUOUS);
        stepByStepItem.setSelected(pace == PresentationPace.STEP_BY_STEP);
        int pause = preferences.aiPauseMillis();
        for (int index = 0; index < pauseItems.size(); index++) {
            pauseItems.get(index).setSelected(PresentationPanel.PAUSE_CHOICES_MILLIS.get(index) == pause);
        }
    }

    private boolean isThisCircuit(TaskInfo info) {
        return shows(info.document());
    }

    /** Whether this window shows {@code document}. */
    boolean shows(DocumentId document) {
        return document.equals(ids.apply(view.openDocument()));
    }

    /** The AI asked for «Presentar» (spec §4): the toggle turns on and the panel says so. */
    void aiSwitchedToPresenting() {
        aiSwitched = true;
        preferences.setAiPresenting(true);
        refreshPanel();
    }

    private void followPreferences() {
        toggle.putValue(Action.SELECTED_KEY, preferences.aiPresenting());
        followPace();
        refreshPanel();
    }

    private void refreshPanel() {
        if (disposed) {
            return;
        }
        if (task == null || !preferences.aiPresenting()) {
            placement.hide();
            return;
        }
        panel.show(task, aiSwitched);
        placement.show();
    }

    /** «×» once the task ended. */
    private void closePanel() {
        task = null;
        thinking.stop();
        refreshPanel();
    }

    // --- The events, on the editor thread ---

    private void started(TaskInfo info) {
        if (isThisCircuit(info)) {
            task = new PresentedTask(info);
            thinking.start();
            refreshPanel();
        }
    }

    private void planChanged(TaskInfo info, List<TaskStage> stages, int currentStage, int currentStep) {
        if (!isThisCircuit(info)) {
            return;
        }
        if (task == null || !task.task().taskId().equals(info.taskId())) {
            task = new PresentedTask(info);
            thinking.start();
        }
        task.planChanged(stages, currentStage, currentStep);
        thinking.heard();
        refreshPanel();
    }

    /** Runs {@code change} on the shown task when the event is about it: VSim heard from the AI. */
    private void onTask(String taskId, Consumer<PresentedTask> change) {
        if (task != null && task.task().taskId().equals(taskId)) {
            change.accept(task);
            thinking.heard();
            refreshPanel();
        }
    }

    private void ended(TaskInfo info) {
        onTask(info.taskId(), PresentedTask::ended);
        stopThinkingIfEnded();
    }

    private void stopThinkingIfEnded() {
        if (task == null || task.isEnded()) {
            thinking.stop();
        }
    }

    /**
     * «Detener», or VSim stopping a task whose AI stopped answering (IA.3): the panel stays, like at the end, with
     * «Deshacer etapa» and × (review M5), and says which.
     */
    private void stopped(TaskInfo info, TaskStopReason reason) {
        if (task != null && task.task().taskId().equals(info.taskId())) {
            aiSwitched = false;
        }
        onTask(info.taskId(), reason == TaskStopReason.AI_SILENT ? PresentedTask::stoppedBecauseTheAiWentSilent
                : PresentedTask::stopped);
        stopThinkingIfEnded();
    }

    /** Moves an event to the editor thread; a view disposed meanwhile ignores it. */
    private void later(Runnable event) {
        editorThread.execute(() -> {
            if (!disposed) {
                event.run();
            }
        });
    }

    private final class OnEditorThread implements PresentationListener {

        @Override
        public void planChanged(TaskInfo info, List<TaskStage> stages, int currentStage, int currentStep) {
            List<TaskStage> copy = List.copyOf(stages);
            later(() -> PresentationFeatures.this.planChanged(info, copy, currentStage, currentStep));
        }

        @Override
        public void stepProposed(StepPreview preview) {
            later(() -> onTask(preview.taskId(), shown -> shown.stepProposed(preview)));
        }

        @Override
        public void stepApplied(StepPreview preview) {
            later(() -> {
                if (task != null && task.task().taskId().equals(preview.taskId())) {
                    aiSwitched = false;
                }
                onTask(preview.taskId(), shown -> shown.stepApplied(preview));
            });
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
            later(() -> onTask(taskId, shown -> shown.stepDiscarded(why)));
        }

        @Override
        public void stepPaced(String taskId, Duration delay) {
            later(() -> onTask(taskId, shown -> shown.stepPaced(delay)));
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
            later(() -> onTask(taskId, shown -> shown.stageChecked(stageIndex, passed, summary)));
        }

        @Override
        public void narrated(Narration narration) {
            later(() -> onTask(narration.taskId(), shown -> shown.narrated(narration)));
        }

        @Override
        public void questionAsked(Question question) {
            later(() -> onTask(question.taskId(), shown -> shown.questionAsked(question)));
        }

        @Override
        public void questionClosed(String taskId, String questionId, boolean answered) {
            later(() -> onTask(taskId, shown -> shown.questionClosed(questionId)));
        }

        /** Not a call from the AI: the «Claude está pensando…» watch is left as it is. */
        @Override
        public void questionUnattended(String taskId, String questionId, Duration silentFor) {
            later(() -> {
                if (task != null && task.task().taskId().equals(taskId)) {
                    task.questionUnattended(questionId, silentFor);
                    refreshPanel();
                }
            });
        }
    }

    private final class TaskEvents implements AutomationListener {

        @Override
        public void taskStarted(TaskInfo info) {
            later(() -> started(info));
        }

        @Override
        public void taskEnded(TaskInfo info, Set<String> changedElementIds) {
            later(() -> ended(info));
        }

        @Override
        public void taskStopped(TaskInfo info) {
            taskStopped(info, TaskStopReason.USER);
        }

        @Override
        public void taskStopped(TaskInfo info, TaskStopReason reason) {
            later(() -> stopped(info, reason));
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        removePresentationListener.run();
        removeTaskListener.run();
        removeActivityListener.run();
        thinking.dispose();
        preferences.removeListener(preferencesListener);
        view.removeHierarchyListener(hierarchyListener);
        placement.dispose();
        panel.dispose();
        removeShortcut.run();
        viewMenu.remove(paceMenu);
        viewMenu.remove(menuItem);
        viewMenu.remove(menuSeparator);
        view.toolBar().remove(toolbarButton);
        view.toolBar().remove(toolbarSeparator);
        view.toolBar().revalidate();
    }

    Action toggle() {
        return toggle;
    }

    PresentationPanel panel() {
        return panel;
    }

    FloatingPlacement placement() {
        return placement;
    }

    ThinkingWatch thinking() {
        return thinking;
    }

    boolean isPanelShown() {
        return placement.isShown();
    }
}
