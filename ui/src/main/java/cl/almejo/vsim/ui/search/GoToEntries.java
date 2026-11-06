package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.WindowMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * «Ir a…» (spec §8.3): what the find bar offers besides the matches on the canvas. The open tabs of the window
 * («lrv32im», «⬚ Ram») and the circuit's components («⬚ Muldiv — componente, 3 usos»), those whose name the query
 * matches (with the find bar's own rules: accents never count, «Aa» and «.*» as set). Choosing a tab brings it to
 * the front; choosing a component enters it (spec §4.4) in the context of its first use, or without values when the
 * circuit does not use it.
 */
final class GoToEntries {

    /** One row: its text and what choosing it does. */
    record Entry(String text, Runnable choose) {
    }

    private GoToEntries() {
    }

    /** The tabs first, then the components in the order the document holds them; none for a query that matches nothing. */
    static List<Entry> of(OpenDocument place, WindowCommands window, CompiledQuery query) {
        if (query.matchesNothing()) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (WindowMenu.Entry tab : window.tabEntries()) {
            if (!tab.header() && query.matches(tab.title())) {
                entries.add(new Entry(tab.title(), tab.focus()));
            }
        }
        OpenDocument circuit = place.parent().orElse(place);
        for (ComponentDefinition definition : circuit.document().definitions()) {
            if (query.matches(definition.name())) {
                List<InstancePaths.InstanceUse> uses = circuit.instancePaths(definition.id());
                Optional<String> context = uses.isEmpty() ? Optional.empty() : Optional.of(uses.getFirst().path());
                entries.add(new Entry(componentText(definition.name(), uses.size()),
                        () -> window.enter(circuit, definition.id(), context)));
            }
        }
        return entries;
    }

    private static String componentText(String name, int uses) {
        return switch (uses) {
            case 0 -> Messages.format("find.goTo.componentUnused", name);
            case 1 -> Messages.format("find.goTo.componentOne", name);
            default -> Messages.format("find.goTo.component", name, uses);
        };
    }
}
