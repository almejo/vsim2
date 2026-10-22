package cl.almejo.vsim.automation.present;

/**
 * What the «Presentar» panel may offer for a task's storyboard now (IA.2): «Deshacer etapa» while some stage still has
 * a step in the circuit, and «Dejar solo el resultado» once the task no longer runs and its stages really are a
 * storyboard (every earlier stage in its own frame, nothing of them joined to what stays).
 */
public record StoryboardState(boolean canUndoStage, boolean canKeepOnlyResult) {

    /** Nothing to offer (an unknown or forgotten task). */
    public static final StoryboardState NONE = new StoryboardState(false, false);
}
