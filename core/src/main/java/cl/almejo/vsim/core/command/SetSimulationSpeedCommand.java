package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.Optional;

/**
 * Chooses the circuit's simulation speed: one undo step. The speed lives in the file; nothing on the canvas changes, so
 * no document listener hears it (the editor applies it to the engine and the speed selector after each undo step).
 */
public final class SetSimulationSpeedCommand extends BeforeAfterCommand<Double> {

    private static final String DESCRIPTION = "command.setSimulationSpeed";

    private SetSimulationSpeedCommand(double before, double after) {
        super(before, after);
    }

    /** Empty when {@code speed} is the circuit's speed already (no empty undo step). */
    public static Optional<SetSimulationSpeedCommand> changing(CircuitDocument document, double speed) {
        if (speed == document.simulationSpeed()) {
            return Optional.empty();
        }
        return Optional.of(new SetSimulationSpeedCommand(document.simulationSpeed(), speed));
    }

    @Override
    protected void set(CircuitDocument document, Double speed) {
        document.setSimulationSpeed(speed);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }
}
