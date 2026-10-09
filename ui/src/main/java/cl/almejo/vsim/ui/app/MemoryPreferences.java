package cl.almejo.vsim.ui.app;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

/** A {@link java.util.prefs.Preferences} tree kept in memory: tests and {@link AppPreferences#inMemory()}. */
final class MemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, MemoryPreferences> children = new HashMap<>();
    private final MemoryPreferences parent;

    MemoryPreferences() {
        this(null, "");
    }

    private MemoryPreferences(MemoryPreferences parent, String name) {
        super(parent, name);
        this.parent = parent;
    }

    @Override
    protected void putSpi(String key, String value) {
        values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
        return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
        values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
        values.clear();
        if (parent != null) {
            parent.children.remove(name());
        }
    }

    @Override
    protected String[] keysSpi() {
        return values.keySet().toArray(String[]::new);
    }

    @Override
    protected String[] childrenNamesSpi() {
        return children.keySet().toArray(String[]::new);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return children.computeIfAbsent(name, childName -> new MemoryPreferences(this, childName));
    }

    @Override
    protected void syncSpi() {
    }

    @Override
    protected void flushSpi() {
    }
}
