package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Every user-visible text. Use literal keys with {@link #text}/{@link #format} (MessagesTest checks them)
 * and the helpers below for keys built from data.
 */
public final class Messages {

    public static final Locale SPANISH = Locale.forLanguageTag("es");

    private static final Logger LOGGER = LoggerFactory.getLogger(Messages.class);
    private static final String BUNDLE_NAME = "i18n.messages";

    private static volatile ResourceBundle current = bundle(chooseLocale(Locale.getDefault()));

    private Messages() {
    }

    /** English when the preferred language is English, Spanish otherwise. */
    public static Locale chooseLocale(Locale preferred) {
        return Locale.ENGLISH.getLanguage().equals(preferred.getLanguage()) ? Locale.ENGLISH : SPANISH;
    }

    public static void initialize(Locale preferred) {
        current = bundle(chooseLocale(preferred));
    }

    public static Locale locale() {
        return current.getLocale();
    }

    public static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    public static String text(String key) {
        try {
            return current.getString(key);
        } catch (MissingResourceException exception) {
            LOGGER.warn("Missing message key {}", key);
            return "!" + key + "!";
        }
    }

    public static String format(String key, Object... arguments) {
        return new MessageFormat(text(key), locale()).format(arguments);
    }

    public static String componentName(String typeId) {
        return textOr("component." + typeId, typeId);
    }

    /** Visible name of a type: the definition name for a user component, the bundle name otherwise. */
    public static String componentName(ComponentType type) {
        return SubcircuitTypes.definitionOf(type).map(ComponentDefinition::name)
                .orElseGet(() -> componentName(type.id()));
    }

    public static String categoryName(ComponentCategory category) {
        return textOr("category." + category.name(), category.name());
    }

    public static String parameterName(String parameterName) {
        return textOr("parameter." + parameterName, parameterName);
    }

    public static String choiceName(String parameterName, String value) {
        return textOr("choice." + parameterName + "." + value, value);
    }

    /** Name of a core command description such as "command.move". */
    public static String commandName(String commandKey) {
        return textOr(commandKey, commandKey);
    }

    public static String readError(CircuitFormatException exception) {
        return format("error.read." + exception.reason().name(), exception.detail());
    }

    private static String textOr(String key, String fallback) {
        return current.containsKey(key) ? current.getString(key) : fallback;
    }
}
