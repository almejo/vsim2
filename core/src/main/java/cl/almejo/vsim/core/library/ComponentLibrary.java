package cl.almejo.vsim.core.library;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The user's components in memory (spec §5.6 "Mis componentes"), mirrored from the library directory by M4-A.
 * Thread-safe. Listeners run on the thread that made the change, once per call that changed something.
 */
public final class ComponentLibrary {

    private static final Comparator<DefinitionBundle> ORDER = Comparator
            .comparing((DefinitionBundle bundle) -> bundle.root().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(bundle -> bundle.root().id());

    private final Map<String, DefinitionBundle> bundles = new HashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public synchronized List<DefinitionBundle> entries() {
        return bundles.values().stream().sorted(ORDER).toList();
    }

    public synchronized Optional<DefinitionBundle> find(String id) {
        return Optional.ofNullable(bundles.get(id));
    }

    public synchronized Set<String> ids() {
        return Set.copyOf(bundles.keySet());
    }

    /** Adds or replaces the entry with the bundle's root id. */
    public void put(DefinitionBundle bundle) {
        boolean changed;
        synchronized (this) {
            changed = !bundle.equals(bundles.put(bundle.root().id(), bundle));
        }
        if (changed) {
            fire();
        }
    }

    public boolean remove(String id) {
        boolean removed;
        synchronized (this) {
            removed = bundles.remove(id) != null;
        }
        if (removed) {
            fire();
        }
        return removed;
    }

    /** Makes the library hold exactly {@code next} (a reload of the directory). */
    public void replaceAll(Collection<DefinitionBundle> next) {
        Map<String, DefinitionBundle> replacement = new HashMap<>();
        for (DefinitionBundle bundle : next) {
            replacement.put(bundle.root().id(), bundle);
        }
        boolean changed;
        synchronized (this) {
            changed = !replacement.equals(bundles);
            bundles.clear();
            bundles.putAll(replacement);
        }
        if (changed) {
            fire();
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
