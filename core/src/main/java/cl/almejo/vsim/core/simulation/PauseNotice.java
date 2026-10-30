package cl.almejo.vsim.core.simulation;

import java.util.List;
import java.util.Objects;

/**
 * Why a part paused the simulation ({@link cl.almejo.vsim.core.component.BehaviorContext#requestPause}): component
 * {@code componentId} (flat id) asked at simulation time {@code time}, saying the message of bundle key
 * {@code messageKey} with {@code arguments}. {@code sequence} grows with every request of the engine, so the UI can
 * tell a new pause from one it already showed (two pauses may say the same at the same time after a «Reiniciar»).
 */
public record PauseNotice(String componentId, String messageKey, List<String> arguments, long time, long sequence) {

    public PauseNotice {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }
}
