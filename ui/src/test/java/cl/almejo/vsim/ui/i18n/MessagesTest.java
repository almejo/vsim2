package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.logic.ImportProblem;
import cl.almejo.vsim.core.search.SearchProblem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesTest {

    private static final Pattern KEY_USAGE = Pattern.compile("Messages\\.(?:text|format)\\(\\s*\"([^\"]+)\"");
    private static final Pattern COMMAND_KEY = Pattern.compile("\"(command\\.[a-zA-Z]+)\"");

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void spanishAndEnglishHaveTheSameKeys() {
        assertThat(Messages.bundle(Locale.ENGLISH).keySet())
                .containsExactlyInAnyOrderElementsOf(Messages.bundle(Messages.SPANISH).keySet());
    }

    /** A lone ' starts a MessageFormat quote, so a formatted text (one with {0}…) must write an apostrophe as ''. */
    @Test
    void everyApostropheInAFormattedTextIsDoubled() {
        Pattern argument = Pattern.compile("\\{\\d");
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            ResourceBundle bundle = Messages.bundle(locale);
            List<String> lone = new ArrayList<>();
            for (String key : bundle.keySet()) {
                String value = bundle.getString(key);
                if (argument.matcher(value).find() && value.replace("''", "").contains("'")) {
                    lone.add(key);
                }
            }
            assertThat(lone).as("formatted texts with a lone apostrophe in %s", locale).isEmpty();
        }
    }

    @Test
    void theGiveUpQuestionKeepsItsApostropheInEnglish() {
        Messages.initialize(Locale.ENGLISH);
        assertThat(Messages.format("karnaugh.practice.giveUp.message", "F"))
                .isEqualTo("Give up on F and see VSim's solution? That map closes: your groups stay, but you can't "
                        + "change them any more.");
    }

    @Test
    void everyLiteralKeyUsedInTheSourcesExists() throws IOException {
        assertThat(missing(literalKeysIn(Path.of("src/main/java"), KEY_USAGE))).isEmpty();
    }

    @Test
    void everyCoreCommandDescriptionHasAName() throws IOException {
        Set<String> commandKeys = literalKeysIn(Path.of("../core/src/main/java"), COMMAND_KEY);
        assertThat(commandKeys).contains("command.move");
        assertThat(missing(commandKeys)).isEmpty();
    }

    @Test
    void everyComponentCategoryParameterAndChoiceHasAName() {
        Set<String> keys = new TreeSet<>();
        for (ComponentCategory category : ComponentCategory.values()) {
            keys.add("category." + category.name());
        }
        for (ComponentType type : StandardComponentTypes.registry().all()) {
            keys.add("component." + type.id());
            for (ParameterSpec parameter : type.parameters()) {
                keys.add("parameter." + parameter.name());
                parameter.choices().forEach(choice -> keys.add("choice." + parameter.name() + "." + choice));
            }
        }
        assertThat(missing(keys)).isEmpty();
    }

    @Test
    void everyBlockHasAPaletteHelp() {
        List<String> keys = new ArrayList<>();
        for (ComponentType type : StandardComponentTypes.registry().all()) {
            if (List.of(ComponentCategory.ARITHMETIC, ComponentCategory.PLEXERS, ComponentCategory.SEQUENTIAL)
                    .contains(type.category()) && !type.id().equals(StandardComponentTypes.FLIP_FLOP_DATA)) {
                keys.add("help.component." + type.id());
            }
        }
        assertThat(keys).contains("help.component." + StandardComponentTypes.ADDER);
        assertThat(missing(keys)).isEmpty();
    }

    @Test
    void everyReadErrorReasonHasAMessage() {
        List<String> keys = new ArrayList<>();
        for (CircuitFormatException.Reason reason : CircuitFormatException.Reason.values()) {
            keys.add("error.read." + reason.name());
        }
        assertThat(missing(keys)).isEmpty();
    }

    @Test
    void everyTableImportProblemHasAMessage() {
        assertThat(missing(ImportProblem.MESSAGE_KEYS)).isEmpty();
    }

    @Test
    void everyExpressionProblemHasAMessage() {
        assertThat(missing(ExpressionProblem.MESSAGE_KEYS)).isEmpty();
    }

    @Test
    void everySearchProblemHasAMessage() {
        assertThat(missing(SearchProblem.MESSAGE_KEYS)).isEmpty();
    }

    @Test
    void englishLocaleSelectsEnglishAndAnyOtherSelectsSpanish() {
        assertThat(Messages.chooseLocale(Locale.US)).isEqualTo(Locale.ENGLISH);
        assertThat(Messages.chooseLocale(Locale.GERMANY)).isEqualTo(Messages.SPANISH);
        assertThat(Messages.chooseLocale(Locale.forLanguageTag("es-CL"))).isEqualTo(Messages.SPANISH);
    }

    @Test
    void textAndFormatUseTheChosenLanguage() {
        Messages.initialize(Locale.ENGLISH);
        assertThat(Messages.text("action.undo")).isEqualTo("Undo");
        assertThat(Messages.format("action.undoNamed", Messages.commandName("command.move"))).isEqualTo("Undo move");
        Messages.initialize(Locale.forLanguageTag("es-CL"));
        assertThat(Messages.format("action.undoNamed", Messages.commandName("command.move"))).isEqualTo("Deshacer mover");
        assertThat(Messages.format("status.shortCircuits", 1)).isEqualTo("⚠ 1 cortocircuito");
        assertThat(Messages.format("status.shortCircuits", 3)).isEqualTo("⚠ 3 cortocircuitos");
    }

    @Test
    void helpersFallBackToTheRawNameAndMissingKeysAreVisible() {
        assertThat(Messages.componentName("not-a-type")).isEqualTo("not-a-type");
        assertThat(Messages.componentName("clock")).isEqualTo("Reloj");
        assertThat(Messages.choiceName("color", "green")).isEqualTo("Verde");
        assertThat(Messages.text("no.such.key")).isEqualTo("!no.such.key!");
        assertThat(Messages.readError(new CircuitFormatException(CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE, "lamp")))
                .isEqualTo("Tipo de componente desconocido: lamp");
    }

    @Test
    void newerAndForeignFilesSayWhatToDo() {
        assertThat(Messages.readError(new CircuitFormatException(CircuitFormatException.Reason.NEWER_FORMAT, "3")))
                .isEqualTo("Este archivo es de una versión más nueva de VSim 2 (formato 3). Actualiza VSim para abrirlo.");
        assertThat(Messages.readError(new CircuitFormatException(CircuitFormatException.Reason.NOT_VSIM_FILE, "x")))
                .isEqualTo("Este archivo no es de VSim.");
        Messages.initialize(java.util.Locale.ENGLISH);
        try {
            assertThat(Messages.readError(new CircuitFormatException(CircuitFormatException.Reason.NEWER_FORMAT, "3")))
                    .isEqualTo("This file is from a newer version of VSim 2 (format 3). Update VSim to open it.");
            assertThat(Messages.readError(new CircuitFormatException(CircuitFormatException.Reason.NOT_VSIM_FILE, "x")))
                    .isEqualTo("This file is not a VSim file.");
        } finally {
            Messages.initialize(Messages.SPANISH);
        }
    }

    @Test
    void subcircuitTypesAreNamedAfterTheirDefinition() {
        assertThat(Messages.componentName(SubcircuitTypes.typeOf(ExampleDefinitions.inverter()))).isEqualTo("Inversor");
        assertThat(Messages.componentName(StandardComponentTypes.registry().require("buffer"))).isEqualTo("Buffer");
    }

    @Test
    void aGateIsNamedAfterItsTypeAndNegateOutput() {
        assertThat(Messages.componentName("and", Map.of("negate", false))).isEqualTo("AND");
        assertThat(Messages.componentName("and", Map.of("negate", true))).isEqualTo("NAND");
        assertThat(Messages.componentName("xor", Map.of("negate", true))).isEqualTo("XNOR");
        assertThat(Messages.componentName("buffer", Map.of("negate", true))).isEqualTo("NOT");
    }

    private static Set<String> literalKeysIn(Path root, Pattern pattern) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(path));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    private static List<String> missing(Iterable<String> keys) {
        List<String> missing = new ArrayList<>();
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            ResourceBundle bundle = Messages.bundle(locale);
            for (String key : keys) {
                if (!bundle.containsKey(key)) {
                    missing.add(locale + ":" + key);
                }
            }
        }
        return missing;
    }
}
