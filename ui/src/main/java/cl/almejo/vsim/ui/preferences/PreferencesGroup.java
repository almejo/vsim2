package cl.almejo.vsim.ui.preferences;

import javax.swing.JComponent;
import java.util.function.ToIntFunction;

/**
 * One entry of the preferences list: its stable {@code id} (stored as the last group seen and used by the command
 * palette), the message key of its title, its page, whether a line separates it from the entries above, and how it
 * answers a search.
 *
 * @param filter applies the query to the page and returns how many matches it has (a blank query shows everything)
 */
record PreferencesGroup(String id, String titleKey, JComponent page, boolean separatorBefore,
                        ToIntFunction<String> filter) {
}
