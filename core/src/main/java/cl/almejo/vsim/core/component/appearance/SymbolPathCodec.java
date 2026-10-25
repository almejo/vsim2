package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Encodes one bounded SVG subpath using only integer M/L/Q/C/Z commands. */
public final class SymbolPathCodec {
    // More than enough for 256 nodes, each with three 32-bit coordinate pairs and a command.
    private static final int MAXIMUM_DATA_LENGTH = 32768;

    private SymbolPathCodec() {
    }

    public static String encode(SymbolPath path) {
        Objects.requireNonNull(path, "path");
        StringBuilder data = new StringBuilder("M ");
        point(data, path.nodes().getFirst().point());
        for (int index = 0; index < path.nodes().size() - 1; index++) {
            segment(data, path.nodes().get(index), path.nodes().get(index + 1).point());
        }
        SymbolNode last = path.nodes().getLast();
        if (path.closed()) {
            // Spell out the closing edge for coincident end nodes as well: they are distinct editable nodes.
            if (last.curved() || last.point().equals(path.nodes().getFirst().point())) {
                segment(data, last, path.nodes().getFirst().point());
            }
            data.append(" Z");
        }
        return data.toString();
    }

    public static SymbolPath decode(String data, boolean filled, GridSize box) {
        Objects.requireNonNull(box, "box");
        if (data == null || data.length() > MAXIMUM_DATA_LENGTH) {
            throw invalid("missing or excessive path data");
        }
        try {
            SymbolPath path = new Parser(data).parse(filled);
            new SymbolDrawing(box, List.of(path), true);
            return path;
        } catch (SymbolPathException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private static void segment(StringBuilder data, SymbolNode from, SymbolPoint to) {
        data.append(from.curve().map(curve -> switch (curve) {
            case SegmentCurve.Quadratic ignored -> " Q ";
            case SegmentCurve.Cubic ignored -> " C ";
        }).orElse(" L "));
        for (SymbolPoint control : from.curve().map(SegmentCurve::controls).orElse(List.of())) {
            point(data, control);
            data.append(' ');
        }
        point(data, to);
    }

    private static void point(StringBuilder data, SymbolPoint point) {
        data.append(point.x()).append(' ').append(point.y());
    }

    private static SymbolPathException invalid(String detail) {
        return new SymbolPathException("appearance.path.invalid", detail);
    }

    /** Small bounded parser rather than a general SVG parser (no relative commands or implicit repetitions). */
    private static final class Parser {
        private final String data;
        private int offset;

        private Parser(String data) {
            this.data = data;
        }

        private SymbolPath parse(boolean filled) {
            require('M');
            List<SymbolNode> nodes = new ArrayList<>();
            nodes.add(SymbolNode.straight(readPoint()));
            boolean closed = false;
            while (hasNext()) {
                char command = next();
                if (command == 'Z') {
                    closed = true;
                    if (hasNext()) {
                        throw invalid("commands after path closure");
                    }
                    break;
                }
                Optional<SegmentCurve> curve = switch (command) {
                    case 'L' -> Optional.empty();
                    case 'Q' -> Optional.of(new SegmentCurve.Quadratic(readPoint()));
                    case 'C' -> Optional.of(new SegmentCurve.Cubic(readPoint(), readPoint()));
                    default -> throw invalid("only L, Q, C and final Z may follow M");
                };
                SymbolPoint end = readPoint();
                int previous = nodes.size() - 1;
                nodes.set(previous, new SymbolNode(nodes.get(previous).point(), curve));
                // SVG must spell out a curved closing edge, but the model stores its curve on the last node.
                if (end.equals(nodes.getFirst().point()) && peek('Z')) {
                    continue;
                }
                nodes.add(SymbolNode.straight(end));
                if (nodes.size() > SymbolPath.MAXIMUM_NODES) {
                    throw invalid("too many path nodes");
                }
            }
            return new SymbolPath(nodes, closed, filled);
        }

        private SymbolPoint readPoint() {
            return new SymbolPoint(integer(), integer());
        }

        private int integer() {
            whitespace();
            int start = offset;
            if (offset < data.length() && data.charAt(offset) == '-') {
                offset++;
            }
            int digits = offset;
            while (offset < data.length() && data.charAt(offset) >= '0' && data.charAt(offset) <= '9') {
                offset++;
            }
            if (offset == digits || offset - start > 11 || offset < data.length()
                    && !Character.isWhitespace(data.charAt(offset)) && !Character.isUpperCase(data.charAt(offset))) {
                throw invalid("integer coordinate expected");
            }
            return Integer.parseInt(data.substring(start, offset));
        }

        private void require(char expected) {
            if (!hasNext() || next() != expected) {
                throw invalid("path must begin with M");
            }
        }

        private char next() {
            whitespace();
            if (offset == data.length()) {
                throw invalid("incomplete path command");
            }
            return data.charAt(offset++);
        }

        private boolean peek(char command) {
            whitespace();
            return offset < data.length() && data.charAt(offset) == command;
        }

        private boolean hasNext() {
            whitespace();
            return offset < data.length();
        }

        private void whitespace() {
            while (offset < data.length() && Character.isWhitespace(data.charAt(offset))) {
                offset++;
            }
        }
    }
}
