package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * M3-B context-menu items: Etiqueta…, Editar texto…, Borrar tramo, Agregar texto aquí; M6.8: Restablecer posición de
 * la etiqueta.
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
            }
            case Wire wire -> menu.add(item(Messages.text("action.removeSegment"),
                    () -> actions.removeWireSegment(wire, point)));
            case Frame frame -> {
            }
        }
    }

    private static JMenuItem item(String text, Runnable behavior) {
        return new JMenuItem(new AbstractAction(text) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        });
    }
}
