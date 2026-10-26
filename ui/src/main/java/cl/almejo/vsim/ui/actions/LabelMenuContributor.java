package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.i18n.NoteColorNames;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

/**
 * M3-B context-menu items: Etiqueta…, Editar texto…, Borrar tramo, Agregar texto aquí; M6.8: Restablecer posición de
 * la etiqueta; M7: Valor… on a bus input pin; Pulido: a note's Estilo ▸.
 */
public final class LabelMenuContributor implements ContextMenuContributor {

    private final LabelActions actions;

    public LabelMenuContributor(LabelActions actions) {
        this.actions = actions;
    }

    /** {@code gridPosition} is the (fractional) grid point where the menu was opened. */
    @Override
    public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                           Point2D gridPosition) {
        GridPoint point = new GridPoint((int) Math.round(gridPosition.getX()), (int) Math.round(gridPosition.getY()));
        if (target.isEmpty()) {
            menu.add(ContextMenuEntry.ADD_TEXT.tag(
                    item(Messages.text("action.addText"), () -> actions.addTextAt(point))));
            return;
        }
        boolean single = selection.ids().size() <= 1;
        switch (target.get()) {
            case Component component -> {
                if (single && InterfacePins.isBusInput(component)) {
                    menu.add(ContextMenuEntry.VALUE.tag(
                            item(Messages.text("action.value"), () -> actions.editValue(component))));
                }
                if (single) {
                    menu.add(ContextMenuEntry.LABEL.tag(
                            item(Messages.text("action.label"), () -> actions.editLabel(component))));
                    if (component.labelOffset().isPresent()) {
                        menu.add(ContextMenuEntry.RESET_LABEL_POSITION.tag(item(
                                Messages.text("action.resetLabelPosition"),
                                () -> actions.resetLabelPosition(component))));
                    }
                }
            }
            case TextLabel label -> {
                if (single) {
                    menu.add(item(Messages.text("action.editText"), () -> actions.editLabel(label)));
                }
                menu.add(styleMenu(label, selection));
            }
            case Wire wire -> menu.add(item(Messages.text("action.removeSegment"),
                    () -> actions.removeWireSegment(wire, point)));
            case Frame frame -> {
            }
        }
    }

    /**
     * «Estilo ▸» with «Fondo ▸», «Letra ▸» and «Borde ▸», each listing the note colours with the clicked note's one
     * chosen. A colour paints every selected note when the clicked one is selected, else only the clicked one.
     */
    private JMenu styleMenu(TextLabel clicked, Selection selection) {
        List<ElementId> notes = selection.ids().contains(clicked.id()) ? List.copyOf(selection.ids())
                : List.of(clicked.id());
        JMenu style = new JMenu(Messages.text("noteStyle.menu"));
        for (NoteStyle.Role role : NoteStyle.Role.values()) {
            JMenu colors = new JMenu(NoteColorNames.of(role));
            ButtonGroup group = new ButtonGroup();
            for (NoteColor color : NoteColor.values()) {
                JRadioButtonMenuItem radio = new JRadioButtonMenuItem(NoteColorNames.of(role, color),
                        clicked.style().color(role) == color);
                radio.addActionListener(event -> actions.styleNotes(notes, role, color));
                group.add(radio);
                colors.add(radio);
            }
            style.add(colors);
        }
        return style;
    }

    private static JMenuItem item(String text, Runnable behavior) {
        return new JMenuItem(Actions.of(text, behavior));
    }
}
