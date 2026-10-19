package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.ui.library.ComponentForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the user chose for «Crear» (spec §3 step 5, §6), headless: the destination and whether the circuit also becomes
 * a library component, with its name. The name follows the table's title until the user types one; it is validated
 * like «Crear componente» (non-blank, not taken ignoring case).
 */
final class CreationForm {

    enum Destination { NEW_CIRCUIT, INSERT }

    private final CreationTarget target;
    private final List<Runnable> listeners = new ArrayList<>();
    private Destination destination = Destination.NEW_CIRCUIT;
    private boolean alsoComponent;
    private String componentName = "";
    private boolean nameTyped;

    CreationForm(CreationTarget target) {
        this.target = target;
    }

    CreationTarget target() {
        return target;
    }

    Destination destination() {
        return destination;
    }

    /** «Insertar» is refused (the destination stays) when there is no circuit to insert into. */
    void setDestination(Destination newDestination) {
        if (newDestination == Destination.INSERT && !target.canInsert()) {
            return;
        }
        if (newDestination != destination) {
            destination = newDestination;
            fire();
        }
    }

    boolean alsoComponent() {
        return alsoComponent;
    }

    void setAlsoComponent(boolean also) {
        if (also != alsoComponent) {
            alsoComponent = also;
            fire();
        }
    }

    String componentName() {
        return componentName;
    }

    /** What the user typed; from then on the title no longer changes it. */
    void setComponentName(String name) {
        String next = name == null ? "" : name;
        nameTyped = true;
        if (!next.equals(componentName)) {
            componentName = next;
            fire();
        }
    }

    /** The table's title, the proposed name while the user has not typed one. */
    void proposeName(String title) {
        String next = title == null ? "" : title.strip();
        if (!nameTyped && !next.equals(componentName)) {
            componentName = next;
            fire();
        }
    }

    /** Why «Crear» is off because of the component's name, ready to show; empty when it is fine or not wanted. */
    Optional<String> problem() {
        if (!alsoComponent) {
            return Optional.empty();
        }
        return DefinitionNames.problem(componentName, target.takenComponentNames())
                .map(problem -> ComponentForm.describe(problem, componentName));
    }

    /** The component's name when one is wanted. */
    Optional<String> wantedComponent() {
        return alsoComponent ? Optional.of(componentName.strip()) : Optional.empty();
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
