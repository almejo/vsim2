package cl.almejo.vsim.mcp;

/**
 * The server could not listen on its port (spec §9.1): {@code otherVsim} says whether the program holding it answered
 * as a VSim server, so the panel can say «Otro VSim ya tiene la IA conectada» instead of «ocupado por otro programa».
 */
public final class PortInUseException extends Exception {

    private final int port;
    private final boolean otherVsim;

    public PortInUseException(int port, boolean otherVsim, Throwable cause) {
        super("Port " + port + " is in use" + (otherVsim ? " by another VSim" : ""), cause);
        this.port = port;
        this.otherVsim = otherVsim;
    }

    public int port() {
        return port;
    }

    public boolean otherVsim() {
        return otherVsim;
    }
}
