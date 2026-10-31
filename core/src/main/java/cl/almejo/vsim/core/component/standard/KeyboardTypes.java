package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.terminal.KeyEncoding;

import java.util.List;
import java.util.Map;

/**
 * The «Teclado» part: a queue of bytes typed from the PC keyboard that a circuit reads on a clock edge (Logisim's
 * Keyboard, with UTF‑8 and more). See {@link KeyboardBehavior} for the pins' behaviour and {@link KeyboardPinout}
 * for where they are.
 */
public final class KeyboardTypes {

    /** «Capacidad»: how many bytes the queue holds. */
    public static final String CAPACITY = "capacity";
    /** «Tercer estado»: D lets go of its net while RE is off. */
    public static final String TRI_STATE = "tri-state";
    /** «Texto inicial»: what the queue holds when the simulation starts. */
    public static final String INITIAL_TEXT = "initial-text";
    /** «Terminal enlazado»: the label of the Terminal whose window types into this Teclado. */
    public static final String LINKED_TERMINAL = "linked-terminal";
    /** «Sonido de tecla». */
    public static final String TYPING_SOUND = "typing-sound";

    /** «Color al escribir»: the colour of the capture look; a look only (display-only), never rebuilds the part. */
    public static final String CAPTURE_COLOR = "capture-color";
    /** The values of {@link #CAPTURE_COLOR}; «automatic» follows the scheme's selection colour. */
    public static final List<String> CAPTURE_COLOR_CHOICES = List.of("automatic", "blue", "green", "purple",
            "orange", "pink", "teal", "yellow");

    public static final int DEFAULT_CAPACITY = 64;
    public static final int MAXIMUM_CAPACITY = 4096;

    private static final String SEVEN = String.valueOf(KeyEncoding.SEVEN_BITS);
    private static final String EIGHT = String.valueOf(KeyEncoding.EIGHT_BITS);
    private static final String GROUP_DATA = "data";
    private static final String GROUP_INTERPRETATION = "interpretation";
    private static final String GROUP_TEACHING = "teaching";
    private static final String GROUP_SOUND = "sound";
    private static final String GROUP_APPEARANCE = "appearance";

    private static final List<ParameterSpec> PARAMETERS = List.of(
            ParameterSpec.integer(CAPACITY, DEFAULT_CAPACITY, 1, MAXIMUM_CAPACITY).inGroup(GROUP_DATA),
            ParameterSpec.choice(KeyEncoding.DATA_WIDTH, EIGHT, List.of(SEVEN, EIGHT)).inGroup(GROUP_DATA),
            ParameterSpec.choice(KeyEncoding.ENCODING, KeyEncoding.UTF8_VALUE,
                    List.of(KeyEncoding.UTF8_VALUE, KeyEncoding.LATIN1_VALUE))
                    .visibleWhen(KeyEncoding.DATA_WIDTH, EIGHT).inGroup(GROUP_DATA),
            StandardParameters.EDGE.inGroup(GROUP_DATA),
            ParameterSpec.bool(TRI_STATE, false).inGroup(GROUP_DATA),
            ParameterSpec.choice(KeyEncoding.ENTER_SENDS, KeyEncoding.LF_VALUE,
                    List.of(KeyEncoding.LF_VALUE, KeyEncoding.CR_VALUE, KeyEncoding.CR_LF_VALUE))
                    .inGroup(GROUP_INTERPRETATION),
            ParameterSpec.choice(KeyEncoding.BACKSPACE_SENDS, KeyEncoding.BS_VALUE,
                    List.of(KeyEncoding.BS_VALUE, KeyEncoding.DEL_VALUE)).inGroup(GROUP_INTERPRETATION),
            ParameterSpec.bool(KeyEncoding.ARROWS_AS_SEQUENCES, false).inGroup(GROUP_INTERPRETATION),
            ParameterSpec.text(INITIAL_TEXT, "").asMultiline().inGroup(GROUP_TEACHING),
            ParameterSpec.text(LINKED_TERMINAL, "").asHidden(),
            ParameterSpec.bool(TYPING_SOUND, false).asDisplayOnly().inGroup(GROUP_SOUND),
            ParameterSpec.choice(CAPTURE_COLOR, CAPTURE_COLOR_CHOICES.get(0), CAPTURE_COLOR_CHOICES)
                    .asDisplayOnly().inGroup(GROUP_APPEARANCE));

    private KeyboardTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(StandardComponentTypes.KEYBOARD,
                ComponentCategory.INPUT_OUTPUT, PARAMETERS, KeyboardTypes::shape, KeyboardBehavior::new));
    }

    private static ComponentShape shape(Map<String, Object> resolved) {
        return KeyboardPinout.shape(KeyEncoding.forParameters(resolved).dataWidth());
    }
}
