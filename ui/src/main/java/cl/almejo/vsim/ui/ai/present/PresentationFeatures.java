package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * «Presentar» in one editor view (spec §4, §5): the toggle (a toolbar button at the end and a checkbox item at the
 * end of Ver, both on one action whose selected state is {@link AppPreferences#aiPresenting()}, remembered and shared
 * by every window) and the floating {@link PresentationPanel}, shown while a task of this window's circuit runs (or
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
    private final JToolBar.Separator toolbarSeparator = new JToolBar.Separator();
    private final JToggleButton toolbarButton;
    private final Runnable removeShortcut;
    private final PresentationPanel panel;
    private final FloatingPlacement placement;
    private final Runnable removePresentationListener;
    private final Runnable removeTaskListener;
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
        this.view = Objects.requireNonNull(view, "view");
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
        followPreferences();
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
        refreshPanel();
    }

    // --- The events, on the editor thread ---

    private void started(TaskInfo info) {
        if (isThisCircuit(info)) {
            task = new PresentedTask(info);
            refreshPanel();
        }
    }

    private void planChanged(TaskInfo info, List<TaskStage> stages, int currentStage, int currentStep) {
        if (!isThisCircuit(info)) {
            return;
        }
        if (task == null || !task.task().taskId().equals(info.taskId())) {
            task = new PresentedTask(info);
        }
        task.planChanged(stages, currentStage, currentStep);
        refreshPanel();
    }

    /** Runs {@code change} on the shown task when the event is about it. */
    private void onTask(String taskId, Consumer<PresentedTask> change) {
        if (task != null && task.task().taskId().equals(taskId)) {
            change.accept(task);
            refreshPanel();
        }
    }

    private void ended(TaskInfo info) {
        onTask(info.taskId(), PresentedTask::ended);
    }

    private void stopped(TaskInfo info) {
        if (task != null && task.task().taskId().equals(info.taskId())) {
            task = null;
            aiSwitched = false;
            refreshPanel();
        }
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
            later(() -> stopped(info));
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        removePresentationListener.run();
        removeTaskListener.run();
        preferences.removeListener(preferencesListener);
        view.removeHierarchyListener(hierarchyListener);
        placement.dispose();
        panel.dispose();
        removeShortcut.run();
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

    boolean isPanelShown() {
        return placement.isShown();
    }
}
