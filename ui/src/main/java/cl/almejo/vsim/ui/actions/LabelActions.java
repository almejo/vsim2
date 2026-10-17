package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.RemoveWireSegmentCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.command.SetLabelOffsetCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.frames.FramePainter;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineEditRequest;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.inline.InlineValidators;
import cl.almejo.vsim.ui.paint.ConstantPainter;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Label edits of one editor, done in place over the canvas: component labels, free texts, frame titles, the value of a
 * Constante, and cutting wire stretches. Each one is one undo step. M7: also the live value of a bus input pin, which
 * goes to the simulation and is no undo step.
 */
public final class LabelActions {

    private final OpenDocument openDocument;
    private final Selection selection;
    private final InlineTextEditor editor;
    private final Action editLabel;

    public LabelActions(OpenDocument openDocument, Selection selection, InlineTextEditor editor) {
        this.openDocument = openDocument;
        this.selection = selection;
        this.editor = editor;
        editLabel = new AbstractAction(Messages.text("action.label")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                singleLabelled().ifPresent(LabelActions.this::editLabel);
            }
        };
        editLabel.putValue(Action.SHORT_DESCRIPTION, Messages.text("tooltip.label"));
        selection.addListener(this::refresh);
        refresh();
    }

    /** "Etiqueta…" for the single selected component or free text (F2, Edit menu). */
    public Action editLabel() {
        return editLabel;
    }

    /**
     * Edits in place a component's label (blank removes it), a free text's text (blank is ignored) or a frame's title
     * (blank is allowed: the bar then shows no text). The editor closes without applying when the element goes away.
     */
    public void editLabel(DocumentElement element) {
        Optional<DocumentElement> current = openDocument.document().element(element.id());
        if (current.isEmpty()) {
            return;
        }
        ElementId id = element.id();
        BooleanSupplier stillThere = () -> openDocument.document().element(id).isPresent();
        switch (current.get()) {
            case Component component -> {
                ComponentType type = openDocument.registry().typeOf(component);
                // a sideways label is drawn left of the body: its editor grows to the left, away from the body;
                // a label above the body or one the user moved (M6.8) is drawn centred, so its editor grows both ways
                boolean sideways = component.rotation() == Rotation.R90 || component.rotation() == Rotation.R270;
                InlineEditRequest.Anchor anchor = component.labelOffset().isPresent() || !sideways
                        ? InlineEditRequest.Anchor.CENTER : InlineEditRequest.Anchor.RIGHT;
                editor.open(new InlineEditRequest(
                        LabelLayout.componentLabelPlace(type, component, component.label()).bounds(),
                        LabelLayout.font(), component.label(), InlineValidators.anyText(),
                        text -> setLabelIfChanged(id, text.strip(), false),
                        anchor), stillThere);
            }
            case TextLabel label -> editor.open(new InlineEditRequest(LabelLayout.freeText(label).bounds(),
                    LabelLayout.font(), label.text(), InlineValidators.anyText(),
                    text -> setLabelIfChanged(id, text.strip(), true)), stillThere);
            case Wire wire -> {
            }
            case Frame frame -> editor.open(new InlineEditRequest(FramePainter.titleTextBounds(frame),
                    FramePainter.titleFont(), frame.title(), InlineValidators.anyText(),
                    text -> setLabelIfChanged(id, text.strip(), false)), stillThere);
        }
    }

    /**
     * Edits in place the value of a Constante. Only a value the type accepts can be confirmed; it is stored
     * normalized, through the same command as Propiedades.
     */
    public void editConstantValue(Component constant) {
        Optional<Component> current = openDocument.document().component(constant.id());
        if (current.isEmpty()) {
            return;
        }
        ElementId id = constant.id();
        ComponentType type = openDocument.registry().typeOf(current.get());
        Map<String, Object> parameters = current.get().parameters();
        editor.open(new InlineEditRequest(ConstantPainter.valueBox(type, current.get()),
                ConstantPainter.valueFont(), String.valueOf(parameters.get("value")),
                InlineValidators.constantValue(type, () -> currentParameters(id, parameters)),
                text -> openDocument.document().component(id).ifPresent(component -> {
                    Map<String, Object> changed = new HashMap<>(component.parameters());
                    changed.put("value", text.strip());
                    if (!type.resolveParameters(changed).equals(type.resolveParameters(component.parameters()))) {
                        openDocument.execute(new ChangeParametersCommand(id, type, changed));
                    }
                }), InlineEditRequest.Anchor.CENTER), () -> openDocument.document().component(id).isPresent());
    }

    /**
     * The value edit of a double click or «Valor…»: the Constante's value, or (M7) the live value of a bus input pin.
     * Anything else is ignored.
     */
    public void editValue(Component component) {
        if (component.typeId().equals(StandardComponentTypes.CONSTANT)) {
            editConstantValue(component);
        } else if (InterfacePins.isBusInput(component)) {
            editInputValue(component);
        }
    }

    /**
     * M7: types the value of a bus input pin in place, over its value box, starting from the live value in the pin's
     * «Mostrar como». Only a value that fits the pin's width (as it is at each keystroke) can be confirmed; it goes to
     * the simulation like a click on a switch (no document change, no undo step).
     */
    public void editInputValue(Component pin) {
        Optional<Component> current = openDocument.document().component(pin.id()).filter(InterfacePins::isBusInput);
        if (current.isEmpty()) {
            return;
        }
        ElementId id = pin.id();
        ComponentType type = openDocument.registry().typeOf(current.get());
        int width = InterfacePins.widthOf(current.get());
        String format = (String) type.resolveParameters(current.get().parameters()).get(ConstantValue.FORMAT);
        long liveBits = switch (openDocument.latestSnapshot().componentState(id.value())) {
            case VisibleState.SwitchState state when state.value().isFullyDefined()
                    && state.value().width() == width -> state.value().bits();
            case null, default -> 0L;
        };
        Supplier<Optional<Component>> stillABusInput = () -> openDocument.document().component(id)
                .filter(InterfacePins::isBusInput);
        editor.open(new InlineEditRequest(InterfacePinPainter.valueBox(type, current.get()).orElseThrow(),
                InterfacePinPainter.valueFont(), ConstantValue.editableText(liveBits, width, format),
                InlineValidators.busValue(() -> stillABusInput.get().map(InterfacePins::widthOf).orElse(width)),
                text -> stillABusInput.get().ifPresent(found -> {
                    int foundWidth = InterfacePins.widthOf(found);
                    ConstantValue.bitsOf(text, foundWidth).ifPresent(
                            bits -> openDocument.setInputValue(id, Value.of(foundWidth, bits)));
                }), InlineEditRequest.Anchor.CENTER), () -> stillABusInput.get().isPresent());
    }

    /** What the Constante has now (the last known ones when it is gone, the editor closes by itself then). */
    private Map<String, Object> currentParameters(ElementId id, Map<String, Object> lastKnown) {
        return openDocument.document().component(id).map(Component::parameters).orElse(lastKnown);
    }

    private void setLabelIfChanged(ElementId id, String text, boolean blankIgnored) {
        if (blankIgnored && text.isEmpty()) {
            return;
        }
        Optional<DocumentElement> current = openDocument.document().element(id);
        String previous = switch (current.orElse(null)) {
            case Component component -> component.label();
            case TextLabel label -> label.text();
            case Frame frame -> frame.title();
            case null, default -> null;
        };
        if (previous != null && !previous.equals(text)) {
            openDocument.execute(new SetLabelCommand(id, text));
        }
    }

    /** Opens an empty editor at {@code point}; confirming adds the text there, selected. Blank adds nothing. */
    public void addTextAt(GridPoint point) {
        TextLabel probe = new TextLabel(new ElementId("probe"), point, "");
        editor.open(new InlineEditRequest(LabelLayout.freeText(probe).bounds(), LabelLayout.font(), "",
                InlineValidators.anyText(), raw -> {
            String text = raw.strip();
            if (text.isEmpty()) {
                return;
            }
            TextLabel label = new TextLabel(openDocument.document().newLabelId(), point, text);
            openDocument.execute(new AddElementsCommand(List.of(label)));
            selection.select(label.id());
        }));
    }

    /** Removes the stretch of {@code wire} around {@code near} ("Borrar tramo"). */
    public void removeWireSegment(Wire wire, GridPoint near) {
        if (openDocument.document().wire(wire.id()).isPresent()) {
            openDocument.execute(new RemoveWireSegmentCommand(wire.id(), near, openDocument.registry()));
        }
    }

    /** M6.8: puts the moved label of {@code component} back at its automatic place, as one undo step. */
    public void resetLabelPosition(Component component) {
        openDocument.document().component(component.id())
                .filter(current -> current.labelOffset().isPresent())
                .ifPresent(current -> openDocument.execute(new SetLabelOffsetCommand(current.id(), Optional.empty())));
    }

    private void refresh() {
        editLabel.setEnabled(singleLabelled().isPresent());
    }

    private Optional<DocumentElement> singleLabelled() {
        Set<ElementId> ids = selection.ids();
        if (ids.size() != 1) {
            return Optional.empty();
        }
        return openDocument.document().element(ids.iterator().next()).filter(element -> !(element instanceof Wire));
    }
}
