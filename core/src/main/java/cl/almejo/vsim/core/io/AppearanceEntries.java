package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPathCodec;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** The persisted, ordered path/text representation shared by definitions and placed overrides. */
final class AppearanceEntries {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ElementEntry(String kind, String d, Boolean filled, Integer x, Integer y, String text,
                        String size, String alignment, String fill, String line, String color, String border) {
    }

    record DrawingEntry(List<ElementEntry> elements, Boolean showPinNames) {
    }

    record PlacementEntry(String source, int x, int y) {
    }

    record AppearanceEntry(int width, int height,
                           @JsonInclude(JsonInclude.Include.NON_NULL) DrawingEntry drawing,
                           List<PlacementEntry> pins) {
    }

    private AppearanceEntries() {
    }

    static DrawingEntry entry(SymbolDrawing drawing) {
        return new DrawingEntry(drawing.elements().stream().map(AppearanceEntries::entry).toList(),
                drawing.showPinNames());
    }

    private static ElementEntry entry(SymbolElement element) {
        return switch (element) {
            case SymbolPath path -> new ElementEntry("path", SymbolPathCodec.encode(path), path.filled(),
                    null, null, null, null, null, colorName(path.fill()), colorName(path.line()), null, null);
            case SymbolText text -> new ElementEntry("text", null, null, text.anchor().x(), text.anchor().y(),
                    text.text().orElse(null), text.size().name().toLowerCase(Locale.ROOT),
                    text.alignment().name().toLowerCase(Locale.ROOT), null, null, colorName(text.color()),
                    colorName(text.border()));
        };
    }

    /** A colour by its lower-case name; {@code NONE} is left out of the file. */
    private static String colorName(NoteColor color) {
        return color == NoteColor.NONE ? null : color.name().toLowerCase(Locale.ROOT);
    }

    private static NoteColor color(String name) {
        return name == null ? NoteColor.NONE : NoteColor.valueOf(name.toUpperCase(Locale.ROOT));
    }

    static AppearanceEntry entry(ComponentAppearance appearance) {
        return new AppearanceEntry(appearance.size().width(), appearance.size().height(),
                appearance.drawing().map(AppearanceEntries::entry).orElse(null), appearance.pins().stream()
                .map(pin -> new PlacementEntry(pin.source().value(), pin.offset().x(), pin.offset().y())).toList());
    }

    static SymbolDrawing drawing(DrawingEntry entry, GridSize box) {
        Objects.requireNonNull(entry, "drawing");
        List<ElementEntry> entries = Objects.requireNonNull(entry.elements(), "drawing elements");
        if (entries.size() > SymbolDrawing.MAXIMUM_PATHS + SymbolDrawing.MAXIMUM_TEXTS) {
            throw new IllegalArgumentException("too many symbol elements");
        }
        List<SymbolElement> elements = new ArrayList<>();
        for (ElementEntry element : entries) {
            Objects.requireNonNull(element, "symbol element");
            if ("path".equals(element.kind())) {
                elements.add(SymbolPathCodec.decode(element.d(), Boolean.TRUE.equals(element.filled()), box)
                        .coloured(color(element.fill()), color(element.line())));
            } else if ("text".equals(element.kind())) {
                elements.add(new SymbolText(new SymbolPoint(Objects.requireNonNull(element.x(), "text x"),
                        Objects.requireNonNull(element.y(), "text y")), Optional.ofNullable(element.text()),
                        TextSize.valueOf(Objects.requireNonNull(element.size(), "text size")
                                .toUpperCase(Locale.ROOT)),
                        TextAlignment.valueOf(Objects.requireNonNull(element.alignment(), "text alignment")
                                .toUpperCase(Locale.ROOT)), color(element.color()), color(element.border())));
            } else {
                throw new IllegalArgumentException("unknown symbol element kind: " + element.kind());
            }
        }
        return new SymbolDrawing(box, elements, entry.showPinNames() == null || entry.showPinNames());
    }

    static ComponentAppearance appearance(AppearanceEntry entry) {
        GridSize size = new GridSize(entry.width(), entry.height());
        List<PlacementEntry> pins = Objects.requireNonNull(entry.pins(), "appearance pins");
        return new ComponentAppearance(size, Optional.ofNullable(entry.drawing()).map(value -> drawing(value, size)),
                pins.stream().map(pin -> new PinPlacement(new ElementId(pin.source()),
                        new GridPoint(pin.x(), pin.y()))).toList());
    }
}
