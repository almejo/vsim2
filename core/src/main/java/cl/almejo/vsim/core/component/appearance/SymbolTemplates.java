package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Monochrome starting symbols and primitive figures, all expressed in eighths of a grid unit. */
public final class SymbolTemplates {
    public enum Template { DEFAULT_BOX, RECTANGLE, SQUARE, CIRCLE, TRIANGLE, ALU, MUX, AND, OR, XOR }

    private SymbolTemplates() { }

    /** A pin's drawn name and where its connection point is, in grid units from the box's corner. */
    public record PinLabel(String text, GridPoint offset) { }

    /** The template for a box with no pins to keep clear and no name to write (thumbnails). */
    public static Optional<SymbolDrawing> drawing(Template template, GridSize size) {
        return drawing(template, size, "", List.of());
    }

    /**
     * The template for a box whose pins are {@code pins} and whose component is called {@code name}: the name goes
     * where it does not cover a pin name.
     */
    public static Optional<SymbolDrawing> drawing(Template template, GridSize size, String name,
                                                  List<PinLabel> pins) {
        if (template == Template.DEFAULT_BOX) {
            return Optional.empty();
        }
        int width = size.width() * SymbolPoint.EIGHTHS;
        int height = size.height() * SymbolPoint.EIGHTHS;
        List<SymbolElement> elements = new ArrayList<>();
        SymbolPoint start = new SymbolPoint(0, 0);
        SymbolPoint end = new SymbolPoint(width, height);
        switch (template) {
            case RECTANGLE -> elements.add(rectangle(start, end, false));
            case SQUARE -> {
                int side = Math.min(width, height);
                elements.add(rectangle(new SymbolPoint((width - side) / 2, (height - side) / 2),
                        new SymbolPoint((width + side) / 2, (height + side) / 2), false));
            }
            case CIRCLE -> {
                int side = Math.min(width, height);
                elements.add(circle(width / 2.0, height / 2.0, side / 2.0));
            }
            case TRIANGLE -> elements.add(triangle(start, end, false));
            case ALU, MUX -> elements.add(polygon(body(template, width, height), true));
            case AND -> elements.add(new SymbolPath(List.of(node(start),
                    controlled(width / 2, 0, width, 0), controlled(width, height / 2, width, height),
                    node(new SymbolPoint(width / 2, height)), node(new SymbolPoint(0, height))), true, true));
            case OR, XOR -> {
                int inset = template == Template.XOR ? width / 8 : 0;
                elements.add(new SymbolPath(List.of(controlled(inset, 0, 3 * width / 4, 0),
                        controlled(width, height / 2, 3 * width / 4, height),
                        controlled(inset, height, width / 2, height / 2)), true, true));
                if (template == Template.XOR) {
                    elements.add(new SymbolPath(List.of(controlled(0, 0, 3 * width / 8, height / 2),
                            node(new SymbolPoint(0, height))), false, false));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported template " + template);
        }
        if (hasName(template)) {
            Optional<String> fixedText = fixedText(template);
            elements.add(NamePlacement.nameText(size, body(template, width, height), fixedText.orElse(name), pins,
                    fixedText));
        }
        return Optional.of(new SymbolDrawing(size, elements, true));
    }

    /** The ALU and MUX trapezoids write their own short text (editable afterwards); the others follow the name. */
    private static Optional<String> fixedText(Template template) {
        return switch (template) {
            case ALU -> Optional.of("ALU");
            case MUX -> Optional.of("MUX");
            default -> Optional.empty();
        };
    }

    private static boolean hasName(Template template) {
        return template == Template.RECTANGLE || template == Template.SQUARE || template == Template.CIRCLE
                || template == Template.ALU
                || template == Template.MUX;
    }

    /** The area inside the template where its name may go. */
    private static List<SymbolPoint> body(Template template, int width, int height) {
        return switch (template) {
            case SQUARE, CIRCLE -> {
                int side = Math.min(width, height);
                yield List.of(new SymbolPoint((width - side) / 2, (height - side) / 2),
                        new SymbolPoint((width + side) / 2, (height - side) / 2),
                        new SymbolPoint((width + side) / 2, (height + side) / 2),
                        new SymbolPoint((width - side) / 2, (height + side) / 2));
            }
            case ALU -> List.of(new SymbolPoint(0, 0), new SymbolPoint(width, height / 4),
                    new SymbolPoint(width, 3 * height / 4), new SymbolPoint(0, height),
                    new SymbolPoint(0, 5 * height / 8), new SymbolPoint(width / 4, height / 2),
                    new SymbolPoint(0, 3 * height / 8));
            case MUX -> List.of(new SymbolPoint(0, 0), new SymbolPoint(width, height / 4),
                    new SymbolPoint(width, 3 * height / 4), new SymbolPoint(0, height));
            default -> List.of(new SymbolPoint(0, 0), new SymbolPoint(width, 0), new SymbolPoint(width, height),
                    new SymbolPoint(0, height));
        };
    }

    public static SymbolPath rectangle(SymbolPoint start, SymbolPoint end, boolean square) {
        SymbolPoint corner = constrainedEnd(start, end, square);
        return polygon(List.of(start, new SymbolPoint(corner.x(), start.y()), corner,
                new SymbolPoint(start.x(), corner.y())), true);
    }

    public static SymbolPath triangle(SymbolPoint start, SymbolPoint end, boolean equal) {
        SymbolPoint corner = constrainedEnd(start, end, equal);
        int left = Math.min(start.x(), corner.x());
        int right = Math.max(start.x(), corner.x());
        int top = Math.min(start.y(), corner.y());
        int bottom = Math.max(start.y(), corner.y());
        return polygon(List.of(new SymbolPoint(left, top), new SymbolPoint(right, (top + bottom) / 2),
                new SymbolPoint(left, bottom)), true);
    }

    public static SymbolPath line(SymbolPoint start, SymbolPoint end, boolean constrained) {
        SymbolPoint corner = end;
        if (constrained) {
            int deltaX = end.x() - start.x();
            int deltaY = end.y() - start.y();
            if (Math.abs(deltaX) > 2 * Math.abs(deltaY)) {
                corner = new SymbolPoint(end.x(), start.y());
            } else if (Math.abs(deltaY) > 2 * Math.abs(deltaX)) {
                corner = new SymbolPoint(start.x(), end.y());
            } else {
                int length = Math.max(Math.abs(deltaX), Math.abs(deltaY));
                corner = new SymbolPoint(start.x() + Integer.signum(deltaX) * length,
                        start.y() + Integer.signum(deltaY) * length);
            }
        }
        return polygon(List.of(start, corner), false);
    }

    public static SymbolPath ellipse(SymbolPoint start, SymbolPoint end, boolean circle) {
        SymbolPoint corner = constrainedEnd(start, end, circle);
        int left = Math.min(start.x(), corner.x());
        int right = Math.max(start.x(), corner.x());
        int top = Math.min(start.y(), corner.y());
        int bottom = Math.max(start.y(), corner.y());
        int middleX = (left + right) / 2;
        int middleY = (top + bottom) / 2;
        return new SymbolPath(List.of(controlled(middleX, top, right, top),
                controlled(right, middleY, right, bottom), controlled(middleX, bottom, left, bottom),
                controlled(left, middleY, left, top)), true, true);
    }

    /** A round circle: eight quadratic arcs, each one's control where the tangents at its ends meet. */
    private static SymbolPath circle(double centreX, double centreY, double radius) {
        int arcs = 8;
        double controlRadius = radius / Math.cos(Math.PI / arcs);
        List<SymbolNode> nodes = new ArrayList<>();
        for (int arc = 0; arc < arcs; arc++) {
            double angle = 2 * Math.PI * arc / arcs - Math.PI / 2;
            double controlAngle = angle + Math.PI / arcs;
            nodes.add(controlled((int) Math.round(centreX + radius * Math.cos(angle)),
                    (int) Math.round(centreY + radius * Math.sin(angle)),
                    (int) Math.round(centreX + controlRadius * Math.cos(controlAngle)),
                    (int) Math.round(centreY + controlRadius * Math.sin(controlAngle))));
        }
        return new SymbolPath(nodes, true, true);
    }

    public static SymbolPath polygon(List<SymbolPoint> points, boolean closed) {
        return new SymbolPath(points.stream().map(SymbolTemplates::node).toList(), closed, closed);
    }

    private static SymbolPoint constrainedEnd(SymbolPoint start, SymbolPoint end, boolean equal) {
        if (!equal) { return end; }
        int side = Math.min(Math.abs(end.x() - start.x()), Math.abs(end.y() - start.y()));
        return new SymbolPoint(start.x() + Integer.signum(end.x() - start.x()) * side,
                start.y() + Integer.signum(end.y() - start.y()) * side);
    }

    private static SymbolNode node(SymbolPoint point) { return new SymbolNode(point, Optional.empty()); }
    private static SymbolNode controlled(int x, int y, int controlX, int controlY) {
        return new SymbolNode(new SymbolPoint(x, y), Optional.of(new SymbolPoint(controlX, controlY)));
    }
}
