package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContentDeletion;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.sticky.StickyCloseConfirmation;
import cl.almejo.vsim.ui.sticky.StickyPart;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The drawing gestures of a machine's diagram (spec 2026-11-14 §1 «Drawing», the gestures agreed with the user):
 * <ul>
 * <li>a double click on empty space creates a state (the next free name {@code Sn}); on a state it edits its name,
 * on an arrow or its condition the condition, inline;</li>
 * <li>a drag from a state's centre moves it (the selected states together), one undo step per drag;</li>
 * <li>near a state's border the border lights up, and a drag from there to another state (or back into the same one,
 * a loop) makes an arrow, whose condition then opens inline;</li>
 * <li>a click selects a state or an arrow, Mayús+click adds or takes it away, a drag on empty space selects what the
 * rubber band encloses;</li>
 * <li>the palette's «Estado» places a state with a click, its «Transición» joins two states with a drag from anywhere
 * in the first; either is used once;</li>
 * <li>«Borrar» (Supr) deletes the selection ({@link ContentDeletion}); Esc drops a tool, a drag or the selection.</li>
 * <li>notes (hito 3): the palette's «Nota» (or the toolbar's «Nota adhesiva») sticks a yellow note with a click and
 * opens its text; a click selects a note, a drag moves it with the rest of the selection, a drag from its bottom-right
 * corner resizes it, a double click edits its text. Notes lie under the states and arrows, so those are hit
 * first. As on a circuit's note, its ✕ asks and deletes it, its «–» folds it into a small paper, and a click on a
 * folded note unfolds it (a drag moves it folded); each is one undo step.</li>
 * </ul>
 * Each edit goes through {@link MachineCanvas#edit}: one undo step of the circuit that belongs to the machine's tab.
 * The same edits are in the Editar menu ({@link #newStateAction()}, {@link #newTransitionAction()},
 * {@link #editTextAction()}), so «Acciones» finds them. EDT only.
 */
final class MachineTool implements MachineCanvas.Gestures, ContentDeletion {

    /** How far from a state's circle, on screen, the pointer still lights its border. */
    private static final double BORDER_BAND_PIXELS = 8;
    /** How far from an arrow, on screen, a click still takes it. */
    /** How far the pointer moves before a press becomes a drag, on screen. */
    private static final double DRAG_THRESHOLD_PIXELS = 4;
    /** Where a new state from the menu goes when the middle of the view is taken: this far to the right each time. */
    private static final double NEW_STATE_STEP = 2.5 * MachineDiagramGeometry.STATE_RADIUS;
    private static final int NEW_STATE_TRIES = 16;
    private static final String CANCEL = "machine-cancel";
    private static final String EDIT_TEXT = "machine-edit-text";

    /** What a press started. */
    private enum DragKind { MOVE, JOIN, BAND, RESIZE }

    /** A press and what it does while the button is down. */
    private static final class Drag {
        private final DragKind kind;
        private final Point2D pressed;
        private final DiagramPoint start;
        /** The state pressed (a move or a join), or "" when it was not one. */
        private final String stateId;
        /** The note pressed (a move or a resize), or "" when it was not one. */
        private final String noteId;
        private final Set<String> stateIds;
        private final Set<String> noteIds;
        private DiagramPoint pointer;
        private boolean moved;

        private Drag(DragKind kind, Point2D pressed, DiagramPoint start, String stateId, String noteId,
                     Set<String> stateIds, Set<String> noteIds) {
            this.kind = kind;
            this.pressed = pressed;
            this.start = start;
            this.pointer = start;
            this.stateId = stateId;
            this.noteId = noteId;
            this.stateIds = Set.copyOf(stateIds);
            this.noteIds = Set.copyOf(noteIds);
        }

        private double deltaX() {
            return pointer.x() - start.x();
        }

        private double deltaY() {
            return pointer.y() - start.y();
        }
    }

    private final MachineCanvas canvas;
    private final MachinePalette palette;
    private final MachineInlineEditor inline;
    /** Where a short reason goes when a gesture does nothing (the status line). */
    private final Consumer<String> notices;
    private final MachineSelection selection;
    private final Action newState;
    private final Action newTransition;
    private final Action editText;
    private StickyCloseConfirmation closeConfirmation = StickyCloseConfirmation.dialog();
    /** The note whose ✕ is under the pointer, drawn at full strength. */
    private Optional<String> closeHovered = Optional.empty();
    /** A plain press on a folded note: a release without moving unfolds it (a single click is enough). */
    private boolean unfoldOnRelease;
    /** The note a click has just unfolded: the second press of that double click is swallowed. */
    private Optional<String> justUnfolded = Optional.empty();
    private Optional<String> litState = Optional.empty();
    private Optional<Drag> drag = Optional.empty();
    /** Mayús was down at the press: the click or the rubber band add to the selection. */
    private boolean adding;
    /** A press on a state that was already selected with others: a release without moving selects it alone. */
    private boolean aloneOnRelease;

    MachineTool(MachineCanvas canvas, MachinePalette palette, MachineInlineEditor inline,
                Consumer<String> notices) {
        this.notices = Objects.requireNonNull(notices, "notices");
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.palette = Objects.requireNonNull(palette, "palette");
        this.inline = Objects.requireNonNull(inline, "inline");
        this.selection = canvas.selection();
        newState = Actions.of(Messages.text("machine.action.newState"), this::addStateInView);
        newTransition = Actions.of(Messages.text("machine.action.newTransition"), this::joinSelectedStates);
        newTransition.putValue(Action.SHORT_DESCRIPTION, Messages.text("machine.action.newTransitionHint"));
        editText = Actions.of(Messages.text("machine.action.editText"), this::editSelectedText);
        editText.putValue(Action.SHORT_DESCRIPTION, Messages.text("machine.action.editTextHint"));
    }

    /** Starts listening to the canvas; returns what stops it. */
    Runnable install() {
        MouseAdapter mouse = new MouseHandler();
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        canvas.showGestures(this);
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL);
        canvas.getActionMap().put(CANCEL, Actions.unnamed(this::cancel));
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), EDIT_TEXT);
        canvas.getActionMap().put(EDIT_TEXT, editText);
        Runnable stopFollowing = selection.addListener(this::refreshActions);
        refreshActions();
        return () -> {
            stopFollowing.run();
            canvas.removeMouseListener(mouse);
            canvas.removeMouseMotionListener(mouse);
            canvas.showGestures(MachineCanvas.Gestures.NONE);
            canvas.getInputMap(JComponent.WHEN_FOCUSED).remove(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            canvas.getInputMap(JComponent.WHEN_FOCUSED).remove(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
            canvas.getActionMap().remove(CANCEL);
            canvas.getActionMap().remove(EDIT_TEXT);
        };
    }

    /** What asks before a note's ✕ deletes it (the circuit note's dialog; tests give a fake). */
    void useCloseConfirmation(StickyCloseConfirmation confirmation) {
        closeConfirmation = Objects.requireNonNull(confirmation, "confirmation");
    }

    /** Editar ▸ «Nuevo estado»: a state in the middle of the view (beside it when that place is taken). */
    Action newStateAction() {
        return newState;
    }

    /** Editar ▸ «Nueva transición»: an arrow from the first selected state to the second (or a loop on one). */
    Action newTransitionAction() {
        return newTransition;
    }

    /** Editar ▸ «Escribir el nombre, la condición o la nota» (F2 on the diagram): the selected one's editor. */
    Action editTextAction() {
        return editText;
    }

    @Override
    public Optional<String> closeHoveredNote() {
        return closeHovered;
    }

    /** The state whose border is lit now: the pointer is near it, or an arrow being drawn points at it. */
    Optional<String> litState() {
        return litState;
    }

    @Override
    public Optional<StateMachine> preview() {
        return drag.filter(current -> current.moved
                        && (current.kind == DragKind.MOVE || current.kind == DragKind.RESIZE))
                .flatMap(current -> canvas.machine().map(machine -> dragged(machine, current)));
    }

    /** {@code machine} as the move or resize {@code current} leaves it. */
    private static StateMachine dragged(StateMachine machine, Drag current) {
        return current.kind == DragKind.RESIZE
                ? MachineEdits.withNoteResized(machine, current.noteId, current.deltaX(), current.deltaY())
                : MachineEdits.withMoved(machine, current.stateIds, current.noteIds, current.deltaX(),
                current.deltaY());
    }

    @Override
    public MachineDiagramPainter.Overlay overlay() {
        Optional<Drag> moving = drag.filter(current -> current.moved);
        return new MachineDiagramPainter.Overlay(litState,
                moving.filter(current -> current.kind == DragKind.JOIN).flatMap(this::pendingArrow),
                moving.filter(current -> current.kind == DragKind.BAND).map(MachineTool::band));
    }

    @Override
    public boolean canDelete() {
        return !selection.isEmpty();
    }

    @Override
    public void delete() {
        Set<String> states = selection.stateIds();
        Set<String> transitions = selection.transitionIds();
        Set<String> notes = selection.noteIds();
        if (canvas.edit(machine -> MachineEdits.without(machine, states, transitions, notes))) {
            selection.clear();
        }
    }

    private void refreshActions() {
        int states = selection.stateIds().size();
        newTransition.setEnabled(selection.transitionIds().isEmpty() && selection.noteIds().isEmpty()
                && (states == 1 || states == 2));
        editText.setEnabled(states + selection.transitionIds().size() + selection.noteIds().size() == 1);
    }

    private void addStateInView() {
        Optional<StateMachine> machine = canvas.machine();
        if (machine.isEmpty()) {
            return;
        }
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine.get());
        DiagramPoint place = canvas.toDiagram(new Point2D.Double(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
        for (int attempt = 0; attempt < NEW_STATE_TRIES && isTaken(geometry, place); attempt++) {
            place = place.translated(NEW_STATE_STEP, 0);
        }
        addState(place);
    }

    private static boolean isTaken(MachineDiagramGeometry geometry, DiagramPoint place) {
        return geometry.stateHitAt(new Point2D.Double(place.x(), place.y()), MachineDiagramGeometry.STATE_RADIUS)
                .isPresent();
    }

    private void joinSelectedStates() {
        List<String> states = new ArrayList<>(selection.stateIds());
        if (states.isEmpty() || states.size() > 2) {
            return;
        }
        join(states.get(0), states.get(states.size() - 1));
    }

    private void editSelectedText() {
        int selected = selection.stateIds().size() + selection.transitionIds().size() + selection.noteIds().size();
        if (selected != 1) {
            return;
        }
        if (!selection.stateIds().isEmpty()) {
            inline.editStateName(selection.stateIds().iterator().next());
        } else if (!selection.transitionIds().isEmpty()) {
            inline.editCondition(selection.transitionIds().iterator().next());
        } else {
            inline.editNote(selection.noteIds().iterator().next());
        }
    }

    /** Esc: drops the gesture under way, else the chosen tool (the toolbar's hand too), else the selection. */
    private void cancel() {
        if (drag.isPresent()) {
            drag = Optional.empty();
            canvas.repaint();
        } else if (canvas.putHandDown()) {
            return;
        } else if (palette.stateButton().isSelected() || palette.transitionButton().isSelected()
                || palette.noteButton().isSelected()) {
            palette.clearTool();
        } else {
            selection.clear();
        }
    }

    /** The note's ✕: asks as a circuit note's does and, on «Despegar», deletes it in one undo step. */
    private void closeNote(MachineNote note) {
        if (closeConfirmation.confirm(canvas, new StickyText(note.text()).firstLine())) {
            canvas.edit(machine -> MachineEdits.without(machine, Set.of(), Set.of(), Set.of(note.id())));
            hoverCloseGone(note.id());
        }
    }

    private void hoverCloseGone(String noteId) {
        if (closeHovered.filter(noteId::equals).isPresent()) {
            closeHovered = Optional.empty();
        }
    }

    /** Folds the note {@code noteId} into its small paper, or unfolds it, in one undo step; whether it changed. */
    private boolean fold(String noteId, boolean folded) {
        hoverCloseGone(noteId);
        return canvas.edit(machine -> machine.withNote(noteId, note -> note.withFolded(folded)));
    }

    private void addState(DiagramPoint centre) {
        Optional<StateMachine> machine = canvas.machine();
        if (machine.isEmpty()) {
            return;
        }
        if (!MachineLimits.canAddState(machine.get())) {
            notices.accept(Messages.format("machine.limit.tooManyStates", MachineLimits.MAXIMUM_STATES));
            return;
        }
        if (canvas.edit(current -> MachineEdits.withNewState(current, centre))) {
            canvas.machine().map(MachineEdits::lastState).map(MachineState::id).ifPresent(selection::selectState);
        }
    }

    /** Sticks a new note with its top-left corner at {@code topLeft}, selects it and opens its text. */
    private void addNote(DiagramPoint topLeft) {
        if (canvas.edit(current -> MachineEdits.withNewNote(current, topLeft))) {
            canvas.machine().map(MachineEdits::lastNote).map(MachineNote::id).ifPresent(id -> {
                selection.selectNote(id);
                inline.editNote(id);
            });
        }
    }

    /** How big a note's resize corner is now, in diagram units (never smaller than a few pixels on screen). */
    private double resizeCorner() {
        return Math.max(MachineNotePainting.RESIZE_CORNER_PIXELS,
                canvas.toDiagramLength(MachineNotePainting.RESIZE_CORNER_PIXELS));
    }

    /** Makes the arrow {@code fromId} → {@code toId}, selects it and opens its condition. */
    private void join(String fromId, String toId) {
        if (canvas.edit(machine -> MachineEdits.withNewTransition(machine, fromId, toId))) {
            canvas.machine().map(MachineEdits::lastTransition).map(MachineTransition::id).ifPresent(id -> {
                selection.selectTransition(id);
                inline.editCondition(id);
            });
        }
    }

    private Optional<Line2D> pendingArrow(Drag current) {
        return canvas.machine().filter(machine -> machine.state(current.stateId).isPresent()).map(machine -> {
            MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
            Point2D centre = geometry.centre(current.stateId);
            double distance = centre.distance(current.pointer.x(), current.pointer.y());
            double radius = geometry.radius(current.stateId);
            Point2D start = distance <= radius ? centre : new Point2D.Double(
                    centre.getX() + (current.pointer.x() - centre.getX()) * radius / distance,
                    centre.getY() + (current.pointer.y() - centre.getY()) * radius / distance);
            return new Line2D.Double(start, asPoint(current.pointer));
        });
    }

    private static Rectangle2D band(Drag current) {
        Rectangle2D band = new Rectangle2D.Double(current.start.x(), current.start.y(), 0, 0);
        band.add(current.pointer.x(), current.pointer.y());
        return band;
    }

    /** How far from a state's circle the border reaches now, in diagram units (a fixed width on screen). */
    private double borderBand() {
        return canvas.toDiagramLength(BORDER_BAND_PIXELS);
    }

    private static Point2D asPoint(DiagramPoint point) {
        return new Point2D.Double(point.x(), point.y());
    }

    private void light(Optional<String> stateId) {
        if (!stateId.equals(litState)) {
            litState = stateId;
            canvas.repaint();
        }
    }

    /** The mouse on the diagram: presses start a gesture, drags carry it, a release ends it. */
    private final class MouseHandler extends MouseAdapter {

        @Override
        public void mouseMoved(MouseEvent event) {
            Optional<StateMachine> machine = canvas.machine();
            if (canvas.isPanning()) { // the hand's cursor stays, nothing lights up
                light(Optional.empty());
                return;
            }
            if (machine.isEmpty()) {
                return;
            }
            Optional<MachineDiagramGeometry.StateHit> hit = MachineDiagramGeometry.of(machine.get())
                    .stateHitAt(asPoint(diagramPoint(event)), borderBand(), canvas.drawingOrder(machine.get()));
            boolean joining = palette.transitionButton().isSelected();
            Optional<MachineDiagramGeometry.StateHit> lit = hit.filter(found -> joining
                    || found.part() == MachineDiagramGeometry.StatePart.BORDER);
            light(lit.map(MachineDiagramGeometry.StateHit::stateId));
            Optional<MachineNotePainting.NoteHit> note = hit.isPresent() ? Optional.empty()
                    : noteUnder(machine.get(), diagramPoint(event));
            hoverClose(note.filter(found -> found.part() == StickyPart.CLOSE)
                    .map(found -> found.note().id()));
            canvas.setCursor(Cursor.getPredefinedCursor(lit.isPresent() ? Cursor.CROSSHAIR_CURSOR
                    : hit.isPresent() ? Cursor.MOVE_CURSOR : noteCursor(note, diagramPoint(event))));
        }

        /** The note and its part under {@code point}, unless an arrow is there or the palette's note is chosen. */
        private Optional<MachineNotePainting.NoteHit> noteUnder(StateMachine machine, DiagramPoint point) {
            Point2D at = asPoint(point);
            if (palette.noteButton().isSelected() || canvas.transitionAt(machine, MachineDiagramGeometry.of(machine),
                    at).isPresent()) {
                return Optional.empty();
            }
            return MachineNotePainting.partAt(machine, at, canvas.viewport().zoom());
        }

        /**
         * The cursor over a note, as over a circuit's: the hand on its ✕ and «–», resizing on its corner, moving
         * elsewhere on it, the arrow off notes.
         */
        private int noteCursor(Optional<MachineNotePainting.NoteHit> note, DiagramPoint point) {
            return note.map(found -> switch (found.part()) {
                case CLOSE, FOLD -> Cursor.HAND_CURSOR;
                default -> MachineNotePainting.onResizeCorner(found.note(), asPoint(point), resizeCorner())
                        ? Cursor.SE_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
            }).orElse(Cursor.DEFAULT_CURSOR);
        }

        private void hoverClose(Optional<String> noteId) {
            if (!noteId.equals(closeHovered)) {
                closeHovered = noteId;
                canvas.repaint();
            }
        }

        @Override
        public void mouseExited(MouseEvent event) {
            hoverClose(Optional.empty());
            if (drag.isEmpty()) {
                light(Optional.empty());
            }
        }

        @Override
        public void mousePressed(MouseEvent event) {
            Optional<StateMachine> machine = canvas.machine();
            if (!SwingUtilities.isLeftMouseButton(event) || machine.isEmpty() || canvas.isPanning()) {
                return;
            }
            DiagramPoint point = diagramPoint(event);
            Point2D at = asPoint(point);
            MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine.get());
            adding = event.isShiftDown();
            aloneOnRelease = false;
            unfoldOnRelease = false;
            Optional<String> unfoldedByTheFirstClick = justUnfolded;
            justUnfolded = Optional.empty();
            if (event.getClickCount() >= 2 && unfoldedByTheFirstClick.isPresent()
                    && MachineNotePainting.noteAt(machine.get(), at).map(MachineNote::id)
                    .equals(unfoldedByTheFirstClick)) {
                return; // the second press of a double click whose first click unfolded the note
            }
            if (event.getClickCount() >= 2) {
                doubleClick(machine.get(), geometry, point);
                return;
            }
            if (palette.noteButton().isSelected()) {
                palette.clearTool();
                addNote(point);
                return;
            }
            Optional<MachineDiagramGeometry.StateHit> hit = geometry.stateHitAt(at, borderBand(),
                    canvas.drawingOrder(machine.get()));
            if (palette.stateButton().isSelected()) {
                if (hit.isEmpty()) {
                    palette.clearTool();
                    addState(point);
                }
                return;
            }
            if (hit.isPresent() && (palette.transitionButton().isSelected()
                    || hit.get().part() == MachineDiagramGeometry.StatePart.BORDER)) {
                startDrag(DragKind.JOIN, event, point, hit.get().stateId(), "", Set.of(), Set.of());
                return;
            }
            if (hit.isPresent()) {
                pressState(event, point, hit.get().stateId());
                return;
            }
            Optional<String> arrow = canvas.transitionAt(machine.get(), geometry, at);
            if (arrow.isPresent()) {
                pressTransition(arrow.get());
                return;
            }
            Optional<MachineNotePainting.NoteHit> note = MachineNotePainting.partAt(machine.get(), at,
                    canvas.viewport().zoom());
            if (note.isPresent()) {
                switch (note.get().part()) {
                    case CLOSE -> closeNote(note.get().note());
                    case FOLD -> fold(note.get().note().id(), true);
                    default -> pressNote(event, point, note.get().note());
                }
                return;
            }
            if (!adding) {
                selection.clear();
            }
            startDrag(DragKind.BAND, event, point, "", "", Set.of(), Set.of());
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (drag.isEmpty()) {
                return;
            }
            Drag current = drag.get();
            current.pointer = diagramPoint(event);
            current.moved |= current.pressed.distance(event.getPoint()) >= DRAG_THRESHOLD_PIXELS;
            if (current.kind == DragKind.JOIN) {
                Optional<String> target = canvas.machine().flatMap(machine -> MachineDiagramGeometry.of(machine)
                        .stateHitAt(asPoint(current.pointer), borderBand(), canvas.drawingOrder(machine)))
                        .map(MachineDiagramGeometry.StateHit::stateId);
                litState = target.or(() -> Optional.of(current.stateId));
            }
            canvas.repaint();
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (drag.isEmpty()) {
                return;
            }
            Drag current = drag.get();
            current.pointer = diagramPoint(event);
            drag = Optional.empty();
            switch (current.kind) {
                case MOVE -> releaseMove(current);
                case JOIN -> releaseJoin(current);
                case BAND -> releaseBand(current);
                case RESIZE -> releaseResize(current);
            }
            canvas.repaint();
        }

        private void doubleClick(StateMachine machine, MachineDiagramGeometry geometry, DiagramPoint point) {
            drag = Optional.empty();
            Point2D at = asPoint(point);
            Optional<String> state = geometry.stateAt(at, canvas.drawingOrder(machine));
            if (state.isPresent()) {
                inline.editStateName(state.get());
                return;
            }
            Optional<String> arrow = canvas.transitionAt(machine, geometry, at);
            if (arrow.isPresent()) {
                inline.editCondition(arrow.get());
                return;
            }
            Optional<MachineNote> note = MachineNotePainting.noteAt(machine, at);
            if (note.isPresent()) {
                selection.selectNote(note.get().id());
                if (note.get().folded()) {
                    fold(note.get().id(), false);
                } else {
                    inline.editNote(note.get().id());
                }
                return;
            }
            addState(point);
        }

        private void pressState(MouseEvent event, DiagramPoint point, String stateId) {
            if (adding) {
                Set<String> states = new LinkedHashSet<>(selection.stateIds());
                if (!states.remove(stateId)) {
                    states.add(stateId);
                }
                selection.set(states, selection.transitionIds(), selection.noteIds());
                return;
            }
            if (selection.containsState(stateId)) {
                aloneOnRelease = selectedCount() > 1;
            } else {
                selection.selectState(stateId);
            }
            startDrag(DragKind.MOVE, event, point, stateId, "", selection.stateIds(), selection.noteIds());
        }

        /** A press on a note: its corner resizes it; elsewhere it is selected (or Mayús toggles it) and moves. */
        private void pressNote(MouseEvent event, DiagramPoint point, MachineNote note) {
            if (!adding && MachineNotePainting.onResizeCorner(note, asPoint(point), resizeCorner())) {
                selection.selectNote(note.id());
                startDrag(DragKind.RESIZE, event, point, "", note.id(), Set.of(), Set.of());
                return;
            }
            if (adding) {
                Set<String> notes = new LinkedHashSet<>(selection.noteIds());
                if (!notes.remove(note.id())) {
                    notes.add(note.id());
                }
                selection.set(selection.stateIds(), selection.transitionIds(), notes);
                return;
            }
            if (selection.containsNote(note.id())) {
                aloneOnRelease = selectedCount() > 1;
            } else {
                selection.selectNote(note.id());
            }
            unfoldOnRelease = note.folded();
            startDrag(DragKind.MOVE, event, point, "", note.id(), selection.stateIds(), selection.noteIds());
        }

        private int selectedCount() {
            return selection.stateIds().size() + selection.transitionIds().size() + selection.noteIds().size();
        }

        private void pressTransition(String transitionId) {
            if (!adding) {
                selection.selectTransition(transitionId);
                return;
            }
            Set<String> transitions = new LinkedHashSet<>(selection.transitionIds());
            if (!transitions.remove(transitionId)) {
                transitions.add(transitionId);
            }
            selection.set(selection.stateIds(), transitions, selection.noteIds());
        }

        private void startDrag(DragKind kind, MouseEvent event, DiagramPoint point, String stateId, String noteId,
                               Set<String> stateIds, Set<String> noteIds) {
            drag = Optional.of(new Drag(kind, event.getPoint(), point, stateId, noteId, stateIds, noteIds));
        }

        private void releaseMove(Drag current) {
            if (current.moved) {
                canvas.edit(machine -> dragged(machine, current));
                return;
            }
            if (unfoldOnRelease && fold(current.noteId, false)) {
                justUnfolded = Optional.of(current.noteId);
            }
            if (aloneOnRelease && !current.stateId.isEmpty()) {
                selection.selectState(current.stateId);
            } else if (aloneOnRelease) {
                selection.selectNote(current.noteId);
            }
        }

        /** The note takes the size the corner was dragged to, as one undo step. */
        private void releaseResize(Drag current) {
            if (current.moved) {
                canvas.edit(machine -> dragged(machine, current));
            }
        }

        /** An arrow to the state under the pointer (a loop when it is the same); a click on a border selects. */
        private void releaseJoin(Drag current) {
            light(Optional.empty());
            if (!current.moved) {
                if (!palette.transitionButton().isSelected()) {
                    selection.selectState(current.stateId);
                }
                return;
            }
            Optional<String> target = canvas.machine().flatMap(machine -> MachineDiagramGeometry.of(machine)
                    .stateHitAt(asPoint(current.pointer), borderBand(), canvas.drawingOrder(machine)))
                    .map(MachineDiagramGeometry.StateHit::stateId);
            if (target.isPresent()) {
                palette.clearTool();
                join(current.stateId, target.get());
            }
        }

        /** Selects what the band encloses (added to what was selected with Mayús). */
        private void releaseBand(Drag current) {
            if (!current.moved) {
                return;
            }
            Optional<StateMachine> machine = canvas.machine();
            if (machine.isEmpty()) {
                return;
            }
            Rectangle2D band = band(current);
            MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine.get());
            Set<String> states = new LinkedHashSet<>(adding ? selection.stateIds() : Set.of());
            Set<String> transitions = new LinkedHashSet<>(adding ? selection.transitionIds() : Set.of());
            Set<String> notes = new LinkedHashSet<>(adding ? selection.noteIds() : Set.of());
            for (MachineState state : machine.get().states()) {
                if (band.contains(geometry.circle(state.id()).getBounds2D())) {
                    states.add(state.id());
                }
            }
            for (MachineTransition transition : machine.get().transitions()) {
                if (band.contains(geometry.arrow(transition.id()).path().getBounds2D())) {
                    transitions.add(transition.id());
                }
            }
            for (MachineNote note : machine.get().notes()) {
                if (band.contains(MachineNotePainting.paperOf(note))) {
                    notes.add(note.id());
                }
            }
            selection.set(states, transitions, notes);
        }

        private DiagramPoint diagramPoint(MouseEvent event) {
            return canvas.toDiagram(event.getPoint());
        }
    }
}
