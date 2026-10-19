package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Records what «Crear» does instead of opening windows, floating fragments or writing the library. */
class FakeCreationTarget implements CreationTarget {

    record NewCircuit(String name, CircuitFragment fragment, String notice) {
    }

    final List<NewCircuit> newCircuits = new ArrayList<>();
    final List<CircuitFragment> floating = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    final List<DefinitionBundle> saved = new ArrayList<>();
    final List<String> takenNames = new ArrayList<>();
    final Set<String> takenIds = new HashSet<>();
    boolean insertable = true;
    Optional<String> saveError = Optional.empty();

    @Override
    public void openNewCircuit(String name, CircuitFragment fragment, String notice) {
        newCircuits.add(new NewCircuit(name, fragment, notice));
    }

    @Override
    public boolean canInsert() {
        return insertable;
    }

    @Override
    public void insertFloating(CircuitFragment fragment) {
        floating.add(fragment);
    }

    @Override
    public void notice(String text) {
        notices.add(text);
    }

    @Override
    public List<String> takenComponentNames() {
        return takenNames;
    }

    @Override
    public Set<String> takenComponentIds() {
        return takenIds;
    }

    @Override
    public Optional<String> saveComponent(DefinitionBundle bundle) {
        if (saveError.isEmpty()) {
            saved.add(bundle);
        }
        return saveError;
    }
}
