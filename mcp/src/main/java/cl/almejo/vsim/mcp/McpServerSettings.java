package cl.almejo.vsim.mcp;

import java.util.Objects;

/**
 * How the MCP server listens: on loopback at {@code port} (0 picks a free one, for tests), asking every request for
 * {@code Authorization: Bearer <secret>} (spec §9).
 */
public record McpServerSettings(int port, String secret) {

    /** The largest request body the server reads (Global Constraints: ≤ 1 MiB). */
    public static final int MAXIMUM_REQUEST_BYTES = 1024 * 1024;
    /** The requests served at once; a further one is turned away with 503 (Global Constraints: ≤ 4). */
    public static final int MAXIMUM_CONCURRENT_REQUESTS = 4;
    /** Shorter secrets are refused: the secret is the only authentication (loopback alone is not, spec §9). */
    public static final int MINIMUM_SECRET_LENGTH = 16;

    public McpServerSettings {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        Objects.requireNonNull(secret, "secret");
        if (secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalArgumentException("the secret needs at least " + MINIMUM_SECRET_LENGTH + " characters");
        }
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "McpServerSettings[port=" + port + "]";
    }
}
