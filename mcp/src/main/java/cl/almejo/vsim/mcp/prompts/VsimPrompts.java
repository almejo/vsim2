package cl.almejo.vsim.mcp.prompts;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The commands that teach how to use the AI in VSim (IA.3): MCP prompts the user picks in the client
 * ({@code /mcp__vsim__guia} in Claude Code). Each one only phrases a request, as the user would write it, in Spanish
 * (the SDK gives the client no way to ask for a language): the same tools and the same rules of
 * {@link cl.almejo.vsim.mcp.VsimServerInstructions} apply, and no prompt changes the circuit by itself.
 */
public final class VsimPrompts {

    /** How Claude Code names an MCP prompt of the {@code vsim} server; other clients show the bare name. */
    public static final String CLAUDE_CODE_PREFIX = "/mcp__vsim__";

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
                    List.of(argument("circuito", "El circuito que quieres, en tus palabras (por ejemplo «un "
                            + "sumador de 4 bits»)", true)),
                    VsimPrompts::build),
            new Definition("exponer", "Mostrar cómo se arma (Presentar)",
                    "Arma el circuito en «Presentar», paso a paso y con sus razones, una etapa por marco.",
                    List.of(argument("circuito", "El circuito que quieres ver armar", true),
                            argument("ritmo", "«paso a paso» (esperas «Siguiente») o «de corrido» (para grabar); "
                                    + "si no lo das, usa el ritmo que elegiste en VSim", false)),
                    VsimPrompts::present),
            new Definition("explicar", "Explicar el circuito abierto",
                    "Explica qué hace el circuito abierto y cómo, sin cambiarlo.",
                    List.of(), arguments -> explain()),
            new Definition("revisar", "Revisar el circuito",
                    "Busca problemas en el circuito abierto y propone cómo arreglarlos, sin aplicarlos.",
                    List.of(), arguments -> review()),
            new Definition("probar", "Probar el circuito",
                    "Prueba el circuito con su tabla de verdad o con entradas y cuenta lo que VSim midió.",
                    List.of(argument("que", "Qué quieres probar (una salida, un caso); si no lo das, la IA "
                            + "elige lo que muestra mejor cómo funciona", false)),
                    VsimPrompts::test),
            new Definition("componente", "Convertir en componente",
                    "Convierte las partes que elijas en un componente, con buenos nombres para sus pines.",
                    List.of(argument("nombre", "El nombre del componente nuevo", false),
                            argument("partes", "Qué partes lo forman; si no lo dices, la IA te pregunta cuáles "
                                    + "tienes seleccionadas", false)),
                    VsimPrompts::component),
            new Definition("exportar", "Exportar para una guía",
                    "Exporta el circuito a PDF, PNG o SVG en la carpeta de exportación, para una guía o apunte.",
                    List.of(argument("formato", "pdf (por omisión), png o svg", false)),
                    VsimPrompts::export));

    private VsimPrompts() {
    }

    /** What the server registers: one specification per prompt. */
    public static List<McpServerFeatures.SyncPromptSpecification> specifications() {
        return DEFINITIONS.stream()
                .map(definition -> new McpServerFeatures.SyncPromptSpecification(definition.prompt(),
                        (exchange, request) -> definition.answer(request.arguments())))
                .toList();
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
        return "Arma en VSim: " + arguments.required("circuito") + ".\n\n"
                + "Planifícalo por etapas que se entiendan solas (por ejemplo, primero un bloque y luego el "
                + "circuito que lo usa), de izquierda a derecha, con cada entrada, salida y bloque nombrado. "
                + WORK_AS_A_TASK + " Envía una etapa entera por cada vsim_edit. Si una etapa da un bloque que "
                + "las siguientes usan, conviértelo en componente sin reemplazar la selección y usa instancias de "
                + "él.\n\n" + CHECK_EVERY_STAGE + " Al final agrega una nota «Prueba: …» que diga cómo probarlo.";
    }

    private static String present(Arguments arguments) {
        String circuit = arguments.required("circuito");
        Optional<String> paceText = arguments.optional("ritmo");
        Optional<Pace> pace = paceText.flatMap(Pace::of);
        String request = paceText.isPresent() && pace.isEmpty() ? circuit + " " + paceText.get() : circuit;
        if (pace.isEmpty()) {
            pace = Pace.mentionedIn(request);
        }
        return "Muéstrame cómo armas en VSim: " + request + ".\n\n"
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
        String target = arguments.optional("que").map(text -> "Quiero probar: " + text + ". ")
                .orElse("Elige tú lo que mejor muestra cómo funciona. ");
        return "Prueba el circuito que tengo abierto en VSim. " + target
                + "Para un circuito combinacional saca su tabla de verdad (vsim_truth_table); para uno secuencial "
                + "pon entradas (vsim_set_inputs), avanza (vsim_step) y lee las señales (vsim_read_signals). "
                + "Cuéntame lo que VSim midió, en una tabla corta, y separa lo que midió de lo que esperabas. "
                + DO_NOT_CHANGE;
    }

    private static String component(Arguments arguments) {
        String parts = arguments.optional("partes").map(text -> "Las partes son: " + text + ".")
                .orElse("Pregúntame qué partes lo forman (las que tengo seleccionadas): puedes mirarlas con "
                        + "vsim_window_picture, pero confírmalas conmigo antes de cambiar nada.");
        String name = arguments.optional("nombre").map(text -> "Llámalo «" + text + "».")
                .orElse("Propónme un nombre corto y claro.");
        return "Convierte partes del circuito que tengo abierto en VSim en un componente, como «Crear componente "
                + "desde selección». " + parts + " " + name + "\n\n"
                + "Dale a sus pines nombres que digan qué son (A, B, Cin, S, Cout…, no in1, in2), con las entradas "
                + "a la izquierda y las salidas a la derecha. " + WORK_AS_A_TASK + " Usa CreateComponent con "
                + "replaceSelection true para que una instancia reemplace las partes, como hace el menú, salvo "
                + "que te pida conservarlas.\n\n" + CHECK_EVERY_STAGE;
    }

    private static String export(Arguments arguments) {
        String format = arguments.optional("formato").map(text -> text.toUpperCase(Locale.ROOT))
                .filter(text -> List.of("PDF", "PNG", "SVG").contains(text)).orElse("PDF");
        String options = switch (format) {
            case "PNG" -> "scale 2 y printBackground true, para que se imprima bien";
            case "SVG" -> "printBackground true, para que se imprima bien";
            default -> "headerFooter true con el nombre del circuito como título y withValues false, para una "
                    + "hoja limpia (blackAndWhite true si te lo pido para fotocopiar)";
        };
        return "Exporta el circuito que tengo abierto en VSim para una guía o apunte: vsim_export con format "
                + format + " y " + options + ". Antes míralo con vsim_picture; si algo se ve mal (etiquetas "
                + "tapadas, cables cruzando partes), dímelo antes de exportar. VSim lo deja en su carpeta de "
                + "exportación con un nombre nuevo: dime cuál.";
    }

    private static McpSchema.PromptArgument argument(String name, String description, boolean required) {
        return McpSchema.PromptArgument.builder(name).description(description).required(required).build();
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

        /** A client that splits the arguments on spaces may leave the pace inside the circuit's text. */
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

        private static String normalized(String text) {
            return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT).replaceAll("[-_«»\"'.,]", " ").trim().replaceAll("\\s+", " ");
        }
    }

    /** The arguments the client sent, as trimmed texts; an empty one counts as not given. */
    private record Arguments(Map<String, Object> values) {

        Optional<String> optional(String name) {
            return Optional.ofNullable(values.get(name)).map(Object::toString).map(String::trim)
                    .filter(text -> !text.isEmpty());
        }

        String required(String name) {
            return optional(name).orElseThrow(() -> McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                    .message("The prompt needs the argument " + name).build());
        }
    }

    private record Definition(String name, String title, String description,
                              List<McpSchema.PromptArgument> arguments, Function<Arguments, String> text) {

        McpSchema.Prompt prompt() {
            return McpSchema.Prompt.builder(name).title(title).description(description).arguments(arguments)
                    .build();
        }

        McpSchema.GetPromptResult answer(Map<String, Object> values) {
            String request = text.apply(new Arguments(values == null ? Map.of() : values));
            McpSchema.PromptMessage message = McpSchema.PromptMessage.builder(McpSchema.Role.USER,
                    McpSchema.TextContent.builder(request).build()).build();
            return McpSchema.GetPromptResult.builder(List.of(message)).description(description).build();
        }
    }
}
