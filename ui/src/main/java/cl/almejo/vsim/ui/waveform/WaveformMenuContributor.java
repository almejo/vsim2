package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Canvas context-menu items that add channels (spec §3.2): «Ver en formas de onda» on a wire or a multi-selection,
 * «Ver pines en formas de onda» on a component. Each add is handed to {@code afterAdd} (open the panel, blink a row,
 * report the cap).
 */
public final class WaveformMenuContributor implements ContextMenuContributor {

    private final ProbeActions actions;
    private final Consumer<ProbeActions.AddResult> afterAdd;

    public WaveformMenuContributor(ProbeActions actions, Consumer<ProbeActions.AddResult> afterAdd) {
        this.actions = actions;
        this.afterAdd = afterAdd;
    }

    @Override
    public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                           Point2D gridPosition) {
        if (target.isEmpty()) {
            return;
        }
        DocumentElement element = target.get();
        if (selection.size() > 1 && selection.contains(element.id())) {
            Set<ElementId> ids = selection.ids();
            if (actions.hasTargets(ids)) {
                menu.add(item("waveform.menu.show", () -> actions.addSelection(ids)));
            }
            return;
        }
        switch (element) {
            case Wire wire -> menu.add(item("waveform.menu.show", () -> actions.addWire(wire.id())));
            case Component component -> menu.add(item("waveform.menu.showPins",
                    () -> actions.addComponentPins(component.id())));
            case TextLabel label -> {
            }
            case Frame frame -> {
            }
            case StickyNote note -> {
            }
        }
    }

    private JMenuItem item(String key, Supplier<ProbeActions.AddResult> add) {
        return new JMenuItem(Actions.of(Messages.text(key), () -> afterAdd.accept(add.get())));
    }
}
