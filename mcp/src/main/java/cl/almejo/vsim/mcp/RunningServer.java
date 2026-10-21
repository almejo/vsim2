package cl.almejo.vsim.mcp;

import java.net.InetAddress;

/** A started MCP server; {@link #stop()} closes its sessions and frees the port. */
public interface RunningServer {

    /** The port it listens on (the chosen one when started with port 0). */
    int port();

    /** The address it listens on: always loopback. */
    InetAddress address();

    /** Stops serving and frees the port; requests in flight get a few seconds to finish. Calling it again is a no-op. */
    void stop();
}
