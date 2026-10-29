package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * «Agregar separador de bus» on a component's bus output pin and «Agregar unión de bus» on a bus input pin: adds the
 * part, wired to the pin, as one undo step, and selects it.
 */
public final class BusPinMenuContributor implements ContextMenuContributor {

    private final OpenDocument openDocument;
    private final Selection selection;
    private final ConnectionPoints connectionPoints;

    public BusPinMenuContributor(OpenDocument openDocument, Selection selection) {
        this.openDocument = openDocument;
        this.selection = selection;
        this.connectionPoints = new ConnectionPoints(openDocument.registry());
    }

    @Override
    public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection clickedSelection,
                           Point2D gridPosition) {
        Optional<BusPinAttachment> plan = connectionPoints.pinNear(openDocument.document(), gridPosition)
                .flatMap(pin -> BusPinAttachment.plan(openDocument.registry(), pin));
        if (plan.isEmpty()) {
            return;
        }
        BusPinAttachment attachment = plan.get();
        String key = StandardComponentTypes.BUS_SPLITTER.equals(attachment.typeId())
                ? "action.addBusSplitter" : "action.addBusJoiner";
        menu.add(new JMenuItem(Actions.of(Messages.text(key), () -> attach(attachment))));
    }

    /** Adds the part and its wire as one command and selects the part. */
    void attach(BusPinAttachment attachment) {
        CircuitDocument document = openDocument.document();
        String typeId = attachment.typeId();
        ComponentType type = openDocument.registry().require(typeId);
        Component part = type.newComponent(document.newComponentId(), attachment.position(), attachment.rotation(),
                Map.of(StandardComponentTypes.WIDTH, attachment.width()), "");
        Wire wire = new Wire(document.newWireId(), attachment.wireStart(), attachment.wireEnd());
        List<Command> steps = List.of(new AddElementsCommand(List.of(part)), new AddWireCommand(wire));
        openDocument.execute(new CompositeCommand("command.add", steps));
        selection.select(part.id());
    }
}
