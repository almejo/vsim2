package cl.almejo.vsim.mcp.prompts;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The commands that teach how to use the AI in VSim (IA.3): MCP prompts the user picks in the client
 * ({@code /mcp__vsim__guia} in Claude Code). Each one only phrases a request, as the user would write it, in Spanish
 * (the SDK gives the client no way to ask for a language): the same tools and the same rules of
 * {@link cl.almejo.vsim.mcp.VsimServerInstructions} apply, and no prompt changes the circuit by itself.
 *
 * <p>Claude Code splits the text typed after the command on spaces, one word per argument, in order (IA.3, its
 * documentation; extra words and quoting are not documented). So each prompt takes at most one free text
 * ({@code pedido}) plus one-word options ({@code ritmo}, {@code formato}), all optional: every value received is
 * joined again in order, the options are recognised by value wherever they came, and a missing or cut request tells
 * the AI to ask the user. With {@code -Dvsim.ai.timing=INFO} VSim logs what each prompt received.
 */
public final class VsimPrompts {

    /** How Claude Code names an MCP prompt of the {@code vsim} server; other clients show the bare name. */
    public static final String CLAUDE_CODE_PREFIX = "/mcp__vsim__";

    /** The same switch as the tools' timing: off unless {@code -Dvsim.ai.timing=INFO}. */
    private static final Logger TIMING = LoggerFactory.getLogger("cl.almejo.vsim.mcp.timing");
    private static final String REQUEST = "pedido";
    private static final String ASK_IF_CUT = "Si mi pedido llegó cortado o vacío (por ejemplo, una sola palabra "
            + "suelta), pregúntame qué quiero antes de empezar.";
    /** For a prompt that works without a request too: only a cut one makes the AI ask. */
    private static final String ASK_IF_CUT_ONLY = "Si mi pedido llegó cortado (por ejemplo, una palabra suelta "
            + "que no se entiende), pregúntame qué quiero antes de seguir.";

    private static final String WORK_AS_A_TASK = "Hazlo como una tarea de VSim (vsim_begin_task con un plan de "
            + "etapas y pasos, vsim_edit nombrando la tarea, vsim_end_task al final). Lee el catálogo una vez "
            + "(vsim_describe_components) y los pines de cada tipo una sola vez.";
    private static final String CHECK_EVERY_STAGE = "Termina cada etapa con vsim_check_stage y cuéntame lo que "
            + "VSim midió: una etapa está lista solo cuando vsim_check_stage devolvió passed true. Si algo falla, "
            + "arréglalo antes de pasar a la etapa siguiente y vuelve a pedir la comprobación.";
    private static final String DO_NOT_CHANGE = "No cambies el circuito: si ves algo que arreglar, propónmelo y "
            + "espera que te lo pida.";

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("guia", "Qué puede hacer la IA en VSim",
                    "Qué puede y qué no puede hacer la IA en VSim, y tres cosas para probar ahora.",
                    List.of(), VsimPrompts::guide),
            new Definition("armar", "Armar un circuito por etapas",
                    "Arma el circuito que pidas, por etapas, comprobando cada una con VSim.",
                    List.of(argument(REQUEST, "El circuito que quieres, en tus palabras (por ejemplo «un "
                            + "sumador de 4 bits»)")),
                    VsimPrompts::build),
            new Definition("exponer", "Mostrar cómo se arma (Presentar)",
                    "Arma el circuito en «Presentar», paso a paso y con sus razones, una etapa por marco.",
                    List.of(argument(REQUEST, "El circuito que quieres ver armar; puedes terminar con «de "
                                    + "corrido» o «paso a paso»"),
                            argument("ritmo", "«paso a paso» (esperas «Siguiente») o «de corrido» (para grabar); "
                                    + "si no lo das, usa el ritmo que elegiste en VSim")),
                    VsimPrompts::present),
            new Definition("explicar", "Explicar el circuito abierto",
                    "Explica qué hace el circuito abierto y cómo, sin cambiarlo.",
                    List.of(), arguments -> explain()),
            new Definition("revisar", "Revisar el circuito",
                    "Busca problemas en el circuito abierto y propone cómo arreglarlos, sin aplicarlos.",
                    List.of(), arguments -> review()),
            new Definition("probar", "Probar el circuito",
                    "Prueba el circuito con su tabla de verdad o con entradas y cuenta lo que VSim midió.",
                    List.of(argument(REQUEST, "Qué quieres probar (una salida, un caso); si no lo das, la IA "
                            + "elige lo que muestra mejor cómo funciona")),
                    VsimPrompts::test),
            new Definition("componente", "Convertir en componente",
                    "Convierte las partes que elijas en un componente, con buenos nombres para sus pines.",
                    List.of(argument(REQUEST, "Su nombre y qué partes lo forman, en tus palabras; si no lo dices, "
                            + "la IA te pregunta cuáles tienes seleccionadas")),
                    VsimPrompts::component),
            new Definition("exportar", "Exportar para una guía",
                    "Exporta el circuito a PDF, PNG o SVG en la carpeta de exportación, para una guía o apunte.",
                    List.of(argument("formato", "pdf (por omisión), png o svg"),
                            argument(REQUEST, "Para qué es o qué quieres en la hoja, en tus palabras")),
                    VsimPrompts::export));

    private VsimPrompts() {
    }

    /** What the server registers: one specification per prompt. */
    public static List<McpServerFeatures.SyncPromptSpecification> specifications() {
        return DEFINITIONS.stream()
                .map(definition -> new McpServerFeatures.SyncPromptSpecification(definition.prompt(),
                        (exchange, request) -> {
                            if (TIMING.isInfoEnabled()) {
                                TIMING.info(received(definition.name(), request.arguments()));
                            }
                            return definition.answer(request.arguments());
                        }))
                .toList();
    }

    /** The log line of what prompt {@code name} received, in the order VSim reads the values. */
    public static String received(String name, Map<String, Object> values) {
        Arguments arguments = argumentsOf(name, values);
        if (arguments.given().isEmpty()) {
            return "prompt " + name + " received nothing";
        }
        return "prompt " + name + " received " + arguments.given().stream()
                .map(given -> given.name() + "=«" + given.value() + "»").collect(Collectors.joining(", "));
    }

    private static Arguments argumentsOf(String name, Map<String, Object> values) {
        List<String> declared = DEFINITIONS.stream().filter(definition -> definition.name().equals(name))
                .findFirst().map(Definition::argumentNames).orElse(List.of());
        return Arguments.of(declared, values == null ? Map.of() : values);
    }

    /** The prompts in the order the help lists them. */
    public static List<McpSchema.Prompt> prompts() {
        return DEFINITIONS.stream().map(Definition::prompt).toList();
    }

    private static String guide(Arguments arguments) {
        return "Cuéntame en pocas líneas qué puedes hacer conmigo en VSim con sus herramientas: leer y explicar los "
                + "circuitos que tengo abiertos; armar o cambiar circuitos por etapas, comprobando cada una con "
                + "VSim; mostrarme cómo los armas en «Presentar» (paso a paso o de corrido); revisar problemas; "
                + "probar con tablas de verdad y entradas; convertir partes en componentes y exportar a PDF, PNG o "
                + "SVG. Dime también lo que no haces por tu cuenta (lo explica el recurso " + HelpResource.URI
                + ") y que puedo deshacer lo que hagas.\n\n"
                + "Luego mira qué circuitos tengo abiertos (vsim_list_documents) y propónme tres cosas concretas "
                + "para probar ahora con ellos, nombrando los comandos de VSim que sirven: "
                + String.join(", ", DEFINITIONS.stream()
                        .map(definition -> CLAUDE_CODE_PREFIX + definition.name()).toList())
                + ". " + DO_NOT_CHANGE;
    }

    private static String build(Arguments arguments) {
        return arguments.text().map(request -> "Arma en VSim: " + request + ".")
                .orElse("Quiero armar un circuito en VSim, pero todavía no te dije qué: pregúntamelo.")
                + " " + ASK_IF_CUT + "\n\n"
                + "Planifícalo por etapas que se entiendan solas (por ejemplo, primero un bloque y luego el "
                + "circuito que lo usa), de izquierda a derecha, con cada entrada, salida y bloque nombrado. "
                + WORK_AS_A_TASK + " Envía una etapa entera por cada vsim_edit. Si una etapa da un bloque que "
                + "las siguientes usan, conviértelo en componente sin reemplazar la selección y usa instancias de "
                + "él.\n\n" + CHECK_EVERY_STAGE + " Al final agrega una nota «Prueba: …» que diga cómo probarlo.";
    }

    private static String present(Arguments arguments) {
        // a value that is only the pace is the pace, wherever it came; otherwise the pace may be inside the text
        Optional<Pace> pace = arguments.given().stream().map(Given::value).map(Pace::of)
                .flatMap(Optional::stream).findFirst();
        Optional<String> request = arguments.textWithout(value -> Pace.of(value).isPresent());
        if (pace.isEmpty()) {
            pace = request.flatMap(Pace::mentionedIn);
        }
        return request.map(text -> "Muéstrame cómo armas en VSim: " + text + ".")
                .orElse("Quiero ver cómo armas un circuito en VSim, pero todavía no te dije cuál: pregúntamelo.")
                + " " + ASK_IF_CUT + "\n\n"
                + "Quiero verlo en «Presentar»: lee el modo con vsim_get_presentation y, si no está, pídelo con "
                + "vsim_set_presentation presenting true. " + paceInstruction(pace) + "\n\n"
                + WORK_AS_A_TASK + " Empieza cada etapa con vsim_begin_stage y ármala en el origen que te da, de "
                + "izquierda a derecha: VSim pone cada etapa en su marco. Envía cada paso como UN vsim_edit con "
                + "todas sus operaciones, nombrando su etapa y su paso, con un texto corto y una razón corta en "
                + "español («Conecto la realimentación» / «así el latch recuerda»); pocos elementos por paso, para "
                + "que pueda seguirlo. Si un paso vuelve PROPOSED, llama a vsim_await_step hasta que diga APPLIED "
                + "antes del siguiente.\n\n" + CHECK_EVERY_STAGE;
    }

    private static String paceInstruction(Optional<Pace> pace) {
        return pace.map(chosen -> switch (chosen) {
            case STEP_BY_STEP -> "Lo quiero paso a paso: justo después de vsim_begin_task dale pace STEP_BY_STEP "
                    + "a vsim_set_presentation (solo para esta tarea); cada paso espera mi «Siguiente».";
            case CONTINUOUS -> "Lo quiero de corrido, para verlo o grabarlo sin hacer clic: justo después de "
                    + "vsim_begin_task dale pace CONTINUOUS y pauseMillis (por ejemplo 1500) a "
                    + "vsim_set_presentation (solo para esta tarea). Si una comprobación falla, VSim se detiene: "
                    + "arréglalo y compruébalo de nuevo antes de seguir, y avísame.";
        }).orElse("El ritmo (paso a paso o de corrido) lo elijo yo en VSim: no le des pace.");
    }

    private static String explain() {
        return "Explícame el circuito que tengo abierto en VSim: qué hace, cómo lo hace (sus bloques y por dónde va "
                + "la señal) y cómo probarlo. Léelo con vsim_read_circuit y mira cómo se ve con vsim_picture; si "
                + "tengo más de uno abierto (vsim_list_documents), pregúntame cuál. Si te sirve, simúlalo o saca "
                + "su tabla de verdad, y di qué midió VSim y qué deduces tú. " + DO_NOT_CHANGE;
    }

    private static String review() {
        return "Revisa el circuito que tengo abierto en VSim: los problemas que VSim ve (vsim_read_problems), "
                + "pines sueltos, salidas en conflicto, cables que cruzan partes, etiquetas que faltan o que se "
                + "tapan, y si hace lo que su nombre o sus notas dicen (con vsim_truth_table o simulándolo). Dame "
                + "una lista corta, de lo más grave a lo menos, con lo que VSim midió en cada punto y cómo lo "
                + "arreglarías. " + DO_NOT_CHANGE;
    }

    private static String test(Arguments arguments) {
        String target = arguments.text().map(text -> "Quiero probar: " + text + ". ")
                .orElse("Elige tú lo que mejor muestra cómo funciona. ");
        return "Prueba el circuito que tengo abierto en VSim. " + target + ASK_IF_CUT_ONLY + " "
                + "Para un circuito combinacional saca su tabla de verdad (vsim_truth_table); para uno secuencial "
                + "pon entradas (vsim_set_inputs), avanza (vsim_step) y lee las señales (vsim_read_signals). "
                + "Cuéntame lo que VSim midió, en una tabla corta, y separa lo que midió de lo que esperabas. "
                + DO_NOT_CHANGE;
    }

    private static String component(Arguments arguments) {
        String wanted = arguments.text().map(text -> "Lo que quiero: " + text + ". Si no dice qué partes lo "
                        + "forman o cómo se llama, pregúntamelo.")
                .orElse("Pregúntame qué partes lo forman (las que tengo seleccionadas) y propónme un nombre corto "
                        + "y claro.");
        return "Convierte partes del circuito que tengo abierto en VSim en un componente, como «Crear componente "
                + "desde selección». " + wanted + " Puedes mirar las partes con vsim_window_picture, pero "
                + "confírmalas conmigo antes de cambiar nada. " + ASK_IF_CUT + "\n\n"
                + "Dale a sus pines nombres que digan qué son (A, B, Cin, S, Cout…, no in1, in2), con las entradas "
                + "a la izquierda y las salidas a la derecha. " + WORK_AS_A_TASK + " Usa CreateComponent con "
                + "replaceSelection true para que una instancia reemplace las partes, como hace el menú, salvo "
                + "que te pida conservarlas.\n\n" + CHECK_EVERY_STAGE;
    }

    private static String export(Arguments arguments) {
        // the format is a word anywhere in what came (a client may move it), the rest is what the user wants
        String format = arguments.words().stream().map(VsimPrompts::formatOf).flatMap(Optional::stream)
                .findFirst().orElse("PDF");
        String wanted = arguments.wordsWithout(word -> formatOf(word).isPresent())
                .map(text -> " Lo que quiero: " + text + ".").orElse("");
        String options = switch (format) {
            case "PNG" -> "scale 2 y printBackground true, para que se imprima bien";
            case "SVG" -> "printBackground true, para que se imprima bien";
            default -> "headerFooter true con el nombre del circuito como título y withValues false, para una "
                    + "hoja limpia (blackAndWhite true si te lo pido para fotocopiar)";
        };
        return "Exporta el circuito que tengo abierto en VSim para una guía o apunte: vsim_export con format "
                + format + " y " + options + "." + wanted + " " + ASK_IF_CUT_ONLY + " Antes míralo con "
                + "vsim_picture; si algo se ve mal (etiquetas tapadas, cables cruzando partes), dímelo antes de exportar. VSim lo deja en su carpeta de "
                + "exportación con un nombre nuevo: dime cuál.";
    }

    /** Every argument is optional: a client that splits the text may leave any of them empty. */
    private static McpSchema.PromptArgument argument(String name, String description) {
        return McpSchema.PromptArgument.builder(name).description(description).required(false).build();
    }

    private static Optional<String> formatOf(String word) {
        String format = Pace.normalized(word).toUpperCase(Locale.ROOT);
        return List.of("PDF", "PNG", "SVG").contains(format) ? Optional.of(format) : Optional.empty();
    }

    /** The pace a user asks for in «Presentar». */
    private enum Pace {
        STEP_BY_STEP, CONTINUOUS;

        static Optional<Pace> of(String text) {
            return switch (normalized(text)) {
                case "paso a paso", "paso", "step by step" -> Optional.of(STEP_BY_STEP);
                case "de corrido", "corrido", "continuo", "seguido", "continuous" -> Optional.of(CONTINUOUS);
                default -> Optional.empty();
            };
        }

        /** A client that splits the arguments on spaces may leave the pace inside the request's text. */
        static Optional<Pace> mentionedIn(String request) {
            String normalized = " " + normalized(request) + " ";
            if (normalized.contains(" de corrido ")) {
                return Optional.of(CONTINUOUS);
            }
            if (normalized.contains(" paso a paso ")) {
                return Optional.of(STEP_BY_STEP);
            }
            return Optional.empty();
        }

        static String normalized(String text) {
            return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT).replaceAll("[-_«»\"'.,]", " ").trim().replaceAll("\\s+", " ");
        }
    }

    /** One value the client sent, trimmed and not empty. */
    private record Given(String name, String value) {
    }

    /**
     * The values the client sent, in the order VSim reads them: the prompt's own arguments as declared, then any
     * other the client named, by name. An empty value counts as not given.
     */
    private record Arguments(List<Given> given) {

        static Arguments of(List<String> declared, Map<String, Object> values) {
            List<String> order = new ArrayList<>(declared);
            values.keySet().stream().filter(name -> !declared.contains(name)).sorted().forEach(order::add);
            List<Given> given = new ArrayList<>();
            for (String name : order) {
                Object value = values.get(name);
                String text = value == null ? "" : value.toString().strip().replaceAll("\\s+", " ");
                if (!text.isEmpty()) {
                    given.add(new Given(name, text));
                }
            }
            return new Arguments(List.copyOf(given));
        }

        /** Every value joined again with spaces, as the user typed it; empty when nothing came. */
        Optional<String> text() {
            return textWithout(value -> false);
        }

        /** The values but those {@code skipped} matches, joined; empty when nothing is left. */
        Optional<String> textWithout(Predicate<String> skipped) {
            String joined = given.stream().map(Given::value).filter(value -> !skipped.test(value))
                    .collect(Collectors.joining(" "));
            return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
        }

        List<String> words() {
            return text().map(joined -> Arrays.asList(joined.split(" "))).orElse(List.of());
        }

        /** The words but those {@code skipped} matches, joined; empty when nothing is left. */
        Optional<String> wordsWithout(Predicate<String> skipped) {
            String joined = words().stream().filter(word -> !skipped.test(word)).collect(Collectors.joining(" "));
            return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
        }
    }

    private record Definition(String name, String title, String description,
                              List<McpSchema.PromptArgument> arguments, Function<Arguments, String> text) {

        McpSchema.Prompt prompt() {
            return McpSchema.Prompt.builder(name).title(title).description(description).arguments(arguments)
                    .build();
        }

        List<String> argumentNames() {
            return arguments.stream().map(McpSchema.PromptArgument::name).toList();
        }

        McpSchema.GetPromptResult answer(Map<String, Object> values) {
            String request = text.apply(Arguments.of(argumentNames(), values == null ? Map.of() : values));
            McpSchema.PromptMessage message = McpSchema.PromptMessage.builder(McpSchema.Role.USER,
                    McpSchema.TextContent.builder(request).build()).build();
            return McpSchema.GetPromptResult.builder(List.of(message)).description(description).build();
        }
    }
}
