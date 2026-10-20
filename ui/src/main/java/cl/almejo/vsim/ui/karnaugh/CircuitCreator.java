package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.ExpressionLine;
import cl.almejo.vsim.core.logic.GeneratedCircuit;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * «Crear» (spec §3 step 5, §6): minimizes the table (or takes the expressions as written), generates the circuit,
 * checks its truth table and, when asked, makes the library component, all on the {@link Background} worker; then,
 * on the EDT, writes the component to the
 * library and opens the new circuit or floats the gates (or the component's instance) over the current one. Only
 * standard types are involved, so the worker uses its own registry and never touches an open document.
 */
final class CircuitCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitCreator.class);

    /**
     * What to create: the table, the options and the choices of {@link CreationForm}. With {@code asWritten} (the
     * lines of «Circuito desde expresiones» without «Simplificar», whose table is {@code spec}) the circuit has one
     * gate per operator instead of the minimized one; only the options' sharing applies then. With
     * {@code minimization} (the student's groups of the Karnaugh practice, «Usar mis grupos») the circuit is built
     * from those terms instead of minimizing the table; the check still compares it with {@code spec}.
     */
    record Request(TruthTableSpec spec, WizardOptions options, CreationForm.Destination destination,
                   Optional<String> componentName, Optional<List<ExpressionLine>> asWritten,
                   Optional<Minimization> minimization) {
        Request {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(options, "options");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(componentName, "componentName");
            Objects.requireNonNull(minimization, "minimization");
            asWritten = asWritten.map(List::copyOf);
        }

        /** The minimized circuit of {@code spec}, or the lines as written. */
        Request(TruthTableSpec spec, WizardOptions options, CreationForm.Destination destination,
                Optional<String> componentName, Optional<List<ExpressionLine>> asWritten) {
            this(spec, options, destination, componentName, asWritten, Optional.empty());
        }

        /** The minimized circuit of {@code spec}. */
        Request(TruthTableSpec spec, WizardOptions options, CreationForm.Destination destination,
                Optional<String> componentName) {
            this(spec, options, destination, componentName, Optional.empty(), Optional.empty());
        }
    }

    /**
     * What happened, ready to show: {@code lines} are the check («✓ El circuito cumple la tabla»), the component's
     * fate and any failure; {@code passed} is true when the circuit was created and gives the table; {@code saved}
     * is false only when the component was asked for and could not be written.
     */
    record Outcome(boolean created, boolean passed, boolean saved, List<String> lines) {
        Outcome {
            lines = List.copyOf(lines);
        }

        /** Created, the check passed and the component (if any) saved: the work is done and the dialog can go. */
        boolean succeeded() {
            return created && passed && saved;
        }

        String text() {
            return String.join(" ", lines);
        }
    }

    private record Built(GeneratedCircuit circuit, TableCircuits.Check check, DefinitionBundle bundle) {
    }

    private final CreationTarget target;
    private final Background background;
    private boolean busy;
    /** Counts the creations started or cancelled: a result whose number is not the latest is dropped. */
    private long generation;

    CircuitCreator(CreationTarget target, Background background) {
        this.target = target;
        this.background = background;
    }

    boolean isBusy() {
        return busy;
    }

    /**
     * Creates what {@code request} asks for; {@code done} hears the outcome on the EDT (at once with an inline
     * background), unless the creation is {@linkplain #cancel() cancelled} first. Ignored while a creation is running.
     */
    void create(Request request, Consumer<Outcome> done) {
        if (busy) {
            return;
        }
        busy = true;
        long job = ++generation;
        Optional<String> definitionId = request.componentName()
                .map(name -> DefinitionIds.slug(name, target.takenComponentIds()));
        background.run(() -> build(request, definitionId), built -> {
            if (job != generation) {
                return;
            }
            busy = false;
            Outcome outcome;
            try {
                outcome = apply(request, built);
            } catch (RuntimeException exception) {
                outcome = failed(request, exception);
            }
            done.accept(outcome);
        }, failure -> {
            if (job == generation) {
                busy = false;
                done.accept(failed(request, failure));
            }
        });
    }

    /**
     * Drops the creation that is running (its dialog was closed): when its result arrives nothing is written to the
     * library, no window opens and nothing floats. The worker is not interrupted; its result is just ignored.
     */
    void cancel() {
        if (busy) {
            generation++;
            busy = false;
        }
    }

    /** The details go to the log; the user reads a fixed sentence, never an internal (English) message. */
    private static Outcome failed(Request request, Throwable failure) {
        LOGGER.error("Could not create the circuit of the table {}", request.spec().title(), failure);
        return new Outcome(false, false, true, List.of(Messages.text("karnaugh.create.failed")));
    }

    /** The worker's part. */
    private static Built build(Request request, Optional<String> definitionId) {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        TruthTableSpec spec = request.spec();
        GeneratedCircuit circuit;
        if (request.asWritten().isPresent()) {
            circuit = CircuitGenerator.generateAsWritten(spec, request.asWritten().get(),
                    request.options().shareTerms(), registry);
        } else {
            Minimization minimization = request.minimization()
                    .orElseGet(() -> Minimizer.minimize(spec, request.options().form()));
            circuit = CircuitGenerator.generate(spec, minimization, request.options().generatorOptions(), registry);
        }
        TableCircuits.Check check = TableCircuits.check(spec, circuit.fragment(), registry);
        DefinitionBundle bundle = definitionId
                .map(id -> TableCircuits.component(spec, circuit.fragment(), id,
                        request.componentName().orElseThrow(), registry))
                .orElse(null);
        return new Built(circuit, check, bundle);
    }

    /** The EDT's part: the library, then the destination. */
    private Outcome apply(Request request, Built built) {
        List<String> lines = new ArrayList<>();
        lines.add(checkText(built.check()));
        CircuitFragment placed = built.circuit().fragment();
        boolean saved = true;
        if (built.bundle() != null) {
            Optional<String> error = target.saveComponent(built.bundle());
            saved = error.isEmpty();
            lines.add(error.orElseGet(() -> Messages.format("karnaugh.create.componentSaved",
                    built.bundle().root().name())));
            if (request.destination() == CreationForm.Destination.INSERT) {
                placed = TableCircuits.instance(built.bundle());
            }
        }
        if (request.destination() == CreationForm.Destination.NEW_CIRCUIT) {
            target.openNewCircuit(request.spec().title(), placed, String.join(" ", lines));
        } else {
            target.insertFloating(placed);
        }
        return new Outcome(true, built.check().passed(), saved, lines);
    }

    /** «✓ El circuito cumple la tabla», or the first row where it does not (a bug: it should never show). */
    static String checkText(TableCircuits.Check check) {
        if (check.passed()) {
            return Messages.text("karnaugh.create.checked");
        }
        if (!check.evaluated()) {
            return Messages.text("karnaugh.create.notChecked");
        }
        TableCircuits.Mismatch first = check.mismatches().getFirst();
        return Messages.format("karnaugh.create.mismatch", String.valueOf(first.row()), first.output(),
                first.expected() == LogicValue.ONE ? "1" : "0");
    }
}
