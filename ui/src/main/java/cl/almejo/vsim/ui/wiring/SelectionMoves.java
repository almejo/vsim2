package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.LockedFrameWalls;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.core.wiring.WireFollow;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.preferences.PreferencesForm;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The one way every move of a selection is made (M6.7): a drag of the select tool, a drag of a locked block, the
 * arrow keys, X/Y in Propiedades and a drag between views. Following, the attached wires stretch
 * ({@link MoveWithWiresCommand}, clean-up included, one undo step); not following, the move of before M6.7
 * ({@link MoveElementsCommand}). After a following move the status bar names the new contacts it made, or, the first
 * time in the session that wires followed, says how to move without them.
 */
public final class SelectionMoves {

    /**
     * The sessions (one {@link AppPreferences} per running application, shared by every window) that already showed
     * the hint. Weak, so the in-memory preferences of tests do not pile up.
     */
    private static final Set<AppPreferences> HINTED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    private final AppPreferences preferences;
    private final Consumer<String> notices;

    /** {@code notices} shows a status-bar notice (the window's status bar). */
    public SelectionMoves(AppPreferences preferences, Consumer<String> notices) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    /** For hosts without a window (tests, a bare canvas): default in-memory preferences, notices go nowhere. */
    public static SelectionMoves detached() {
        return new SelectionMoves(AppPreferences.inMemory(), text -> {
        });
    }

    public AppPreferences preferences() {
        return preferences;
    }

    /** The follow mode of the toolbar toggle: what moves without a modifier (arrows, Propiedades) do. */
    public boolean followMode() {
        return preferences.wiresFollow();
    }

    /** Whether a drag with these keys down drags the wires: the follow mode, inverted by the configured key. */
    public boolean follows(boolean shiftDown, boolean altDown) {
        return WiresFollow.followsFor(preferences, shiftDown, altDown);
    }

    /** The command of a move of {@code ids} by the delta, with or without the wires following. */
    public static Command command(OpenDocument openDocument, Collection<ElementId> ids, int deltaX, int deltaY,
                                  boolean follow) {
        return follow
                ? MoveWithWiresCommand.of(openDocument.document(), openDocument.registry(),
                new LinkedHashSet<>(ids), deltaX, deltaY)
                : new MoveElementsCommand(ids, deltaX, deltaY);
    }

    /** The delta a move of {@code ids} may take: contents of a locked frame that stays put stop at its border. */
    public static GridPoint limited(OpenDocument openDocument, Collection<ElementId> ids, int deltaX, int deltaY) {
        return LockedFrameWalls.limit(openDocument.document(), openDocument.registry(), ids, deltaX, deltaY);
    }

    /**
     * Moves {@code ids} as one undo step (limited by the locked frames, see {@link #limited}) and reports what the
     * wires did; nothing for no ids or no delta.
     */
    public void move(OpenDocument openDocument, Collection<ElementId> ids, int deltaX, int deltaY, boolean follow) {
        if (ids.isEmpty()) {
            return;
        }
        GridPoint delta = limited(openDocument, ids, deltaX, deltaY);
        if (delta.x() == 0 && delta.y() == 0) {
            return;
        }
        Command command = command(openDocument, ids, delta.x(), delta.y(), follow);
        openDocument.execute(command);
        report(openDocument.document(), command, follow == followMode());
    }

    /**
     * A keyboard move by the follow mode; a run of them is one undo step equal to one move by the total (the
     * commands merge in the history). The report reads the merged command, so it covers the whole run.
     */
    public void nudge(OpenDocument openDocument, Collection<ElementId> ids, int deltaX, int deltaY) {
        GridPoint delta = ids.isEmpty() ? new GridPoint(0, 0) : limited(openDocument, ids, deltaX, deltaY);
        if (delta.x() == 0 && delta.y() == 0) {
            return;
        }
        if (!followMode()) {
            openDocument.execute(MoveElementsCommand.nudge(ids, delta.x(), delta.y()));
            return;
        }
        openDocument.execute(MoveWithWiresCommand.nudge(openDocument.registry(), new LinkedHashSet<>(ids), delta.x(),
                delta.y()));
        openDocument.history().undoCommand().ifPresent(command -> report(openDocument.document(), command, true));
    }

    /**
     * What the wires would do for a move of {@code ids} by the delta, for a preview: wires it would add get throwaway
     * ids, so previews never use up the document's ids.
     */
    public static FollowPlan preview(OpenDocument openDocument, Set<ElementId> ids, int deltaX, int deltaY) {
        int[] previewWires = {0};
        return WireFollow.plan(openDocument.document(), openDocument.registry(), ids, deltaX, deltaY,
                () -> new ElementId("preview-wire-" + previewWires[0]++));
    }

    /** The new contacts, or the hint once per session when wires followed by the mode (not by the invert key). */
    private void report(CircuitDocument document, Command command, boolean followedByMode) {
        if (!(command instanceof MoveWithWiresCommand moved)) {
            return;
        }
        FollowPlan plan = moved.plan().orElse(FollowPlan.EMPTY);
        if (!plan.newContacts().isEmpty()) {
            notices.accept(contactNotice(document, plan.newContacts()));
            return;
        }
        if (followedByMode && wiresChanged(plan) && HINTED.add(preferences)) {
            notices.accept(hint());
        }
    }

    private static boolean wiresChanged(FollowPlan plan) {
        return !plan.movedWholeWireIds().isEmpty() || !plan.replacedWires().isEmpty()
                || !plan.addedWires().isEmpty() || !plan.removedWireIds().isEmpty();
    }

    /** «El cable quedó tocando el pin «Y» de «NOT U1»», «… otro cable», or «El movimiento dejó N contactos nuevos». */
    static String contactNotice(CircuitDocument document, List<NewContact> contacts) {
        if (contacts.size() > 1) {
            return Messages.format("wiresFollow.contacts", contacts.size());
        }
        NewContact contact = contacts.get(0);
        Optional<Component> component = contact.componentId().flatMap(document::component);
        if (component.isEmpty() || contact.pinName().isEmpty()) {
            return Messages.text("wiresFollow.contactWire");
        }
        return Messages.format("wiresFollow.contactPin", contact.pinName().get(),
                componentName(component.get(), document));
    }

    /** The type's visible name, then the label when there is one («NOT U1»). */
    private static String componentName(Component component, CircuitDocument document) {
        String typeName = SubcircuitTypes.definitionIdOf(component.typeId()).flatMap(document::definition)
                .map(ComponentDefinition::name)
                .orElseGet(() -> Messages.componentName(component.typeId()));
        return component.label().isBlank() ? typeName
                : Messages.format("wiresFollow.componentLabeled", typeName, component.label());
    }

    private String hint() {
        AppPreferences.InvertModifier modifier = preferences.invertModifier();
        return modifier == AppPreferences.InvertModifier.NONE
                ? Messages.format("wiresFollow.hintNoKey", Messages.text("action.wiresFollow"))
                : Messages.format("wiresFollow.hint", PreferencesForm.invertModifierName(modifier));
    }
}
