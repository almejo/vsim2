package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The «Multiplexores» parts: mux, decoder, demultiplexer and priority encoder. Registered from here so the categories
 * of block parts do not meet in {@link StandardComponentTypes}.
 */
final class PlexerTypes {

    static final String DEMUX = "demux";
    static final String PRIORITY_ENCODER = "priority-encoder";

    /** «Entradas» of the mux: 2, 4 or 8 data inputs. */
    private static final ParameterSpec MUX_INPUTS = ParameterSpec.choice("inputs", "2", List.of("2", "4", "8"));
    /** «Bits de entrada» of the decoder: 1..4 address bits, so 2..16 outputs. */
    private static final ParameterSpec DECODER_INPUT_BITS = ParameterSpec.integer("inputBits", 2, 1, 4);
    /** «Salidas» of the demultiplexer: 2, 4 or 8. */
    private static final ParameterSpec DEMUX_OUTPUTS = ParameterSpec.choice("outputs", "2", List.of("2", "4", "8"));
    /** «Entradas» of the priority encoder: 2, 4, 8 or 16 (same name as the mux's, so the bundles share the texts). */
    private static final ParameterSpec ENCODER_INPUTS = ParameterSpec.choice("inputs", "4",
            List.of("2", "4", "8", "16"));

    private PlexerTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(StandardComponentTypes.MUX, ComponentCategory.PLEXERS,
                List.of(BlockSignals.WIDTH, MUX_INPUTS, StandardComponentTypes.DELAY),
                parameters -> muxShape(count(parameters, "inputs"), width(parameters)),
                parameters -> new MuxBehavior(count(parameters, "inputs"), width(parameters), delay(parameters))));
        registry.register(ComponentType.parameterized(StandardComponentTypes.DECODER, ComponentCategory.PLEXERS,
                List.of(DECODER_INPUT_BITS, StandardComponentTypes.DELAY),
                parameters -> decoderShape((Integer) parameters.get("inputBits")),
                parameters -> new DecoderBehavior((Integer) parameters.get("inputBits"), delay(parameters))));
        registry.register(ComponentType.parameterized(DEMUX, ComponentCategory.PLEXERS,
                List.of(BlockSignals.WIDTH, DEMUX_OUTPUTS, StandardComponentTypes.DELAY),
                parameters -> demuxShape(count(parameters, "outputs"), width(parameters)),
                parameters -> new DemuxBehavior(count(parameters, "outputs"), width(parameters),
                        delay(parameters))));
        registry.register(ComponentType.parameterized(PRIORITY_ENCODER, ComponentCategory.PLEXERS,
                List.of(ENCODER_INPUTS, StandardComponentTypes.DELAY),
                parameters -> encoderShape(count(parameters, "inputs")),
                parameters -> new PriorityEncoderBehavior(count(parameters, "inputs"), delay(parameters))));
    }

    private static int count(Map<String, Object> parameters, String name) {
        return Integer.parseInt((String) parameters.get(name));
    }

    private static int width(Map<String, Object> parameters) {
        return (Integer) parameters.get(StandardComponentTypes.WIDTH);
    }

    private static int delay(Map<String, Object> parameters) {
        return (Integer) parameters.get("delay");
    }

    /**
     * A 6 x (2k + 4) trapezoid: D0..D(k-1)[N] on the left at y = 3, 5, ..., S[log2 k] in the middle of the bottom edge
     * and Y[N] at (6, 4), the middle of the right edge with 2 inputs. D0, D1 and Y stay put when the inputs grow (S,
     * on the bottom edge, has to move), so their wires stay connected.
     */
    private static ComponentShape muxShape(int inputs, int width) {
        int height = 2 * inputs + 4;
        List<PinSpec> pins = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            pins.add(PinSpec.input("D" + index, 0, 3 + 2 * index, width));
        }
        pins.add(PinSpec.input("S", 3, height, Integer.numberOfTrailingZeros(inputs)));
        pins.add(PinSpec.output("Y", 6, 4, width));
        return new ComponentShape(new GridSize(6, height), pins);
    }

    /**
     * A 6 x (2 * 2^k + 2) rectangle: A[k] at (0, 3) on the left edge, EN in the middle of the bottom edge and
     * Y0..Y(2^k-1) on the right edge at y = 2, 4, ... A and the first outputs stay put when «Bits de entrada» changes
     * (EN, on the bottom edge, has to move).
     */
    private static ComponentShape decoderShape(int inputBits) {
        int outputs = 1 << inputBits;
        int height = 2 * outputs + 2;
        List<PinSpec> pins = new ArrayList<>();
        pins.add(PinSpec.input("A", 0, 3, inputBits));
        pins.add(PinSpec.input("EN", 3, height));
        for (int output = 0; output < outputs; output++) {
            pins.add(PinSpec.output("Y" + output, 6, 2 + 2 * output));
        }
        return new ComponentShape(new GridSize(6, height), pins);
    }

    /**
     * A 6 x (2k + 4) rectangle: D[N] in the middle of the left edge, S[log2 k] in the middle of the bottom edge and
     * Y0..Y(k-1)[N] on the right edge at y = 3, 5, ... (the mux turned around).
     */
    private static ComponentShape demuxShape(int outputs, int width) {
        int height = 2 * outputs + 4;
        List<PinSpec> pins = new ArrayList<>();
        pins.add(PinSpec.input("D", 0, height / 2, width));
        pins.add(PinSpec.input("S", 3, height, Integer.numberOfTrailingZeros(outputs)));
        for (int output = 0; output < outputs; output++) {
            pins.add(PinSpec.output("Y" + output, 6, 3 + 2 * output, width));
        }
        return new ComponentShape(new GridSize(6, height), pins);
    }

    /**
     * A 6 x (2k + 2) rectangle: I0..I(k-1) on the left edge at y = 2, 4, ...; A[log2 k] at (6, 2) and V at (6, 4) on
     * the right edge.
     */
    private static ComponentShape encoderShape(int inputs) {
        List<PinSpec> pins = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            pins.add(PinSpec.input("I" + index, 0, 2 + 2 * index));
        }
        pins.add(PinSpec.output("A", 6, 2, Integer.numberOfTrailingZeros(inputs)));
        pins.add(PinSpec.output("V", 6, 4));
        return new ComponentShape(new GridSize(6, 2 * inputs + 2), pins);
    }
}
