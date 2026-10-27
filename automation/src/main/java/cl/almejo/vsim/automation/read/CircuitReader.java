package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.read.CircuitView.FrameView;
import cl.almejo.vsim.automation.read.CircuitView.GuideView;
import cl.almejo.vsim.automation.read.CircuitView.NetView;
import cl.almejo.vsim.automation.read.CircuitView.NoteView;
import cl.almejo.vsim.automation.read.CircuitView.PartView;
import cl.almejo.vsim.automation.read.CircuitView.PinView;
import cl.almejo.vsim.automation.read.CircuitView.StickyNoteView;
import cl.almejo.vsim.automation.read.CircuitView.Summary;
import cl.almejo.vsim.automation.read.CircuitView.WireView;
import cl.almejo.vsim.automation.read.ReadFilter.Detail;
import cl.almejo.vsim.automation.read.ReadFilter.ElementKind;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Reads a circuit for the AI (spec §10 «read a circuit»): a summary of the whole circuit and one page of the elements
 * the filter keeps, in paint order. Pins are named by {@link PinReference}, never by pixels; nets are lists of pin
 * references and wire ids, valid with the version they were read at.
 */
public final class CircuitReader {

    private static final String CURSOR_SEPARATOR = ":";

    private CircuitReader() {
    }

    /**
     * One page of {@code source} under {@code filter}, starting where {@code cursor} (from the previous page's
     * {@link CircuitView#nextCursor()}) says, or at the start.
     *
     * @throws AutomationException {@link AutomationError#STALE_DOCUMENT} for a cursor of another version (with
     *                             {@code currentVersion}); {@link AutomationError#INVALID_REFERENCE} for a broken one
     */
    public static CircuitView read(ReadSource source, ReadFilter filter, Optional<String> cursor) {
        int offset = cursor.map(text -> offsetOf(text, source)).orElse(0);
        List<DocumentElement> matching = source.document().elements().stream()
                .filter(element -> keeps(filter, element, source))
                .toList();
        List<DocumentElement> page = matching.subList(Math.min(offset, matching.size()),
                Math.min(offset + filter.pageSize(), matching.size()));
        boolean full = filter.detail() == Detail.FULL;
        List<PartView> parts = new ArrayList<>();
        List<WireView> wires = new ArrayList<>();
        List<FrameView> frames = new ArrayList<>();
        List<NoteView> notes = new ArrayList<>();
        List<StickyNoteView> stickyNotes = new ArrayList<>();
        Map<NetId, Net> nets = new LinkedHashMap<>();
        for (DocumentElement element : page) {
            switch (element) {
                case Component component -> parts.add(part(source, component, full, nets));
                case Wire wire -> wires.add(wire(source, wire, nets));
                case Frame frame -> frames.add(new FrameView(frame.id().value(), frame.title(),
                        new Bounds(frame.topLeft().x(), frame.topLeft().y(), frame.width(), frame.height()),
                        frame.tint().name(), frame.locked()));
                case TextLabel label -> notes.add(NoteView.of(label));
                case StickyNote stickyNote -> stickyNotes.add(StickyNoteView.of(stickyNote));
            }
        }
        List<GuideView> guides = offset == 0
                ? source.document().guides().stream()
                        .map(guide -> new GuideView(guide.orientation().name(), guide.position())).toList()
                : List.of();
        List<NetView> netViews = full
                ? nets.values().stream().map(net -> net(source, net)).toList()
                : List.of();
        int next = offset + page.size();
        Optional<String> nextCursor = next < matching.size()
                ? Optional.of(source.version().value() + CURSOR_SEPARATOR + next)
                : Optional.empty();
        return new CircuitView(source.id(), source.version(), source.name(), summary(source), parts, wires, frames,
                notes, stickyNotes, guides, netViews, matching.size(), nextCursor);
    }

    private static int offsetOf(String cursor, ReadSource source) {
        String[] parts = cursor.split(CURSOR_SEPARATOR, -1);
        long version;
        int offset;
        try {
            if (parts.length != 2) {
                throw new NumberFormatException(cursor);
            }
            version = Long.parseLong(parts[0]);
            offset = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE,
                    "Not a cursor of readCircuit: " + cursor, Map.of("hint", "read again without a cursor"));
        }
        if (version != source.version().value()) {
            throw new AutomationException(AutomationError.STALE_DOCUMENT,
                    "The circuit changed since that page was read; read again without a cursor",
                    Map.of("currentVersion", source.version().value()));
        }
        if (offset < 0) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "Not a cursor of readCircuit: " + cursor);
        }
        return offset;
    }

    private static boolean keeps(ReadFilter filter, DocumentElement element, ReadSource source) {
        if (!filter.listedKinds().contains(kindOf(element))) {
            return false;
        }
        if (!filter.elementIds().isEmpty() && !filter.elementIds().contains(element.id().value())) {
            return false;
        }
        return filter.region().map(region -> boundsOf(element, source).intersects(region)).orElse(true);
    }

    private static ElementKind kindOf(DocumentElement element) {
        return switch (element) {
            case Component component -> ElementKind.PART;
            case Wire wire -> ElementKind.WIRE;
            case Frame frame -> ElementKind.FRAME;
            case TextLabel label -> ElementKind.NOTE;
            case StickyNote stickyNote -> ElementKind.STICKY_NOTE;
        };
    }

    /** The element's box; a part of a type the circuit cannot resolve is a point at its position. */
    private static Bounds boundsOf(DocumentElement element, ReadSource source) {
        if (element instanceof Component component) {
            return source.registry().findTypeOf(component).map(type -> type.bounds(component))
                    .orElse(new Bounds(component.position().x(), component.position().y(), 0, 0));
        }
        return ElementBounds.of(element, source.registry());
    }

    private static PartView part(ReadSource source, Component component, boolean full, Map<NetId, Net> nets) {
        Optional<ComponentType> type = source.registry().findTypeOf(component);
        Map<String, Object> parameters = type.map(found -> resolved(found, component)).orElse(component.parameters());
        List<PinView> pins = new ArrayList<>();
        if (full && type.isPresent()) {
            for (int index = 0; index < type.get().pinCount(); index++) {
                pins.add(pin(source, component, type.get(), index, nets));
            }
        }
        return new PartView(component.id().value(), component.typeId(), component.label(), component.position(),
                component.rotation().name(), parameters, boundsOf(component, source), pins,
                SubcircuitTypes.definitionIdOf(component.typeId()).map(definition -> component.appearance().isPresent()));
    }

    private static Map<String, Object> resolved(ComponentType type, Component component) {
        try {
            return type.resolveParameters(component.parameters());
        } catch (IllegalArgumentException exception) {
            return component.parameters();
        }
    }

    private static PinView pin(ReadSource source, Component component, ComponentType type, int index,
                               Map<NetId, Net> nets) {
        PinSpec spec = type.pin(index);
        Optional<Net> net = source.netlist().netForPin(new PinRef(component.id(), index));
        net.ifPresent(found -> nets.putIfAbsent(found.id(), found));
        int width = spec.isAdaptiveWidth() ? net.map(Net::width).orElse(1) : spec.width();
        boolean connected = net.map(found -> !found.wires().isEmpty() || found.pins().size() > 1).orElse(false);
        return new PinView(PinReference.of(component.id().value(), type, index), spec.direction().name(),
                width, type.pinLocation(component, index), net.map(found -> found.id().value()).orElse(""),
                connected);
    }

    private static WireView wire(ReadSource source, Wire wire, Map<NetId, Net> nets) {
        Optional<Net> net = source.netlist().netForWire(wire.id());
        net.ifPresent(found -> nets.putIfAbsent(found.id(), found));
        return new WireView(wire.id().value(), wire.start(), wire.end(),
                net.map(found -> found.id().value()).orElse(""));
    }

    private static NetView net(ReadSource source, Net net) {
        CircuitDocument document = source.document();
        List<PinReference> pins = net.pins().stream()
                .sorted(Comparator.comparingLong((PinRef pin) -> document.orderOf(pin.componentId()))
                        .thenComparingInt(PinRef::pinIndex))
                .map(pin -> reference(source, pin))
                .flatMap(Optional::stream)
                .toList();
        List<String> wires = net.wires().stream()
                .sorted(Comparator.comparingLong(document::orderOf))
                .map(ElementId::value)
                .toList();
        return new NetView(net.id().value(), net.width(), net.widthMismatch(), pins, wires);
    }

    /** {@code pin} as the AI names it, empty when its part or type is gone. */
    static Optional<PinReference> reference(ReadSource source, PinRef pin) {
        return source.document().component(pin.componentId())
                .flatMap(component -> source.registry().findTypeOf(component))
                .filter(type -> pin.pinIndex() < type.pinCount())
                .map(type -> PinReference.of(pin.componentId().value(), type, pin.pinIndex()));
    }

    private static Summary summary(ReadSource source) {
        CircuitDocument document = source.document();
        Map<String, Integer> partsByType = new TreeMap<>();
        document.components().forEach(component -> partsByType.merge(component.typeId(), 1, Integer::sum));
        Bounds bounds = null;
        for (DocumentElement element : document.elements()) {
            Bounds elementBounds = boundsOf(element, source);
            bounds = bounds == null ? elementBounds : bounds.union(elementBounds);
        }
        return new Summary(document.components().size(), document.wires().size(), document.frames().size(),
                document.labels().size(), document.stickyNotes().size(), document.guides().size(), source.netlist().nets().size(),
                Optional.ofNullable(bounds), partsByType,
                document.definitions().stream().map(ComponentDefinition::name).toList());
    }
}
