package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.i18n.Messages;

/** What the AI server is doing, as the panel says it in words and colour (spec §2, §9.1). */
public sealed interface AiServerState {

    /** «Apagada»: not listening (the default). */
    record Off() implements AiServerState {
    }

    /** «Escuchando en 127.0.0.1:<port>»: waiting for Claude Code. */
    record Listening(int port) implements AiServerState {
    }

    /** «Claude Code conectado»: it has asked something since the server started. */
    record Connected(int port) implements AiServerState {
    }

    /** «IA trabajando: <task>»: a task is running (the latest one started when there are several). */
    record Working(int port, String taskTitle) implements AiServerState {
    }

    /** «El puerto <n> está ocupado por otro programa»: offers «Usar el siguiente libre». */
    record PortInUse(int port) implements AiServerState {
    }

    /** «Otro VSim ya tiene la IA conectada; ciérralo o usa ese». */
    record OtherVsim(int port) implements AiServerState {
    }

    /** The words the panel shows. Ports go as text, so they never get a thousands separator. */
    default String text() {
        return switch (this) {
            case Off off -> Messages.text("ai.state.off");
            case Listening listening -> Messages.format("ai.state.listening", Integer.toString(listening.port()));
            case Connected connected -> Messages.text("ai.state.connected");
            case Working working -> Messages.format("ai.state.working", working.taskTitle());
            case PortInUse portInUse -> Messages.format("ai.state.portInUse", Integer.toString(portInUse.port()));
            case OtherVsim otherVsim -> Messages.text("ai.state.otherVsim");
        };
    }

    /** True for the two errors: the server could not start. */
    default boolean isError() {
        return this instanceof PortInUse || this instanceof OtherVsim;
    }

    /** True while the server listens (with or without Claude Code, working or not). */
    default boolean isListening() {
        return this instanceof Listening || this instanceof Connected || this instanceof Working;
    }
}
