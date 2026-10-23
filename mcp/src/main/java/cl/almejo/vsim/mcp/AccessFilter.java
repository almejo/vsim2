package cl.almejo.vsim.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * The checks in front of the MCP endpoint (spec §9, A§10.1), in this order: the Host names this loopback server (421,
 * against DNS rebinding), a browser Origin is local (403), the secret is right (401), the body is not too large (413),
 * the server is not stopping (503) and at most {@link McpServerSettings#MAXIMUM_CONCURRENT_REQUESTS} POSTs are served
 * at once (503). The long-lived GET stream a client keeps open for server messages is not counted, or one client would
 * hold a slot for ever. {@link #drain} lets the POSTs being served finish before the server closes its sessions.
 */
final class AccessFilter extends HttpFilter {

    /** The host names a loopback server answers to; anything else in Host or Origin is another site. */
    private static final Set<String> LOOPBACK_NAMES = Set.of("127.0.0.1", "localhost", "[::1]");
    private static final String BEARER = "Bearer ";
    private static final int MISDIRECTED_REQUEST = 421;

    private final byte[] expectedAuthorization;
    private final IntSupplier port;
    private final Semaphore requestSlots = new Semaphore(McpServerSettings.MAXIMUM_CONCURRENT_REQUESTS);
    /** The server is stopping: every new request is turned away. */
    private volatile boolean stopping;

    /** @param port the port the server listens on, known only once it started (port 0 picks one) */
    AccessFilter(String secret, IntSupplier port) {
        this.expectedAuthorization = (BEARER + secret).getBytes(StandardCharsets.UTF_8);
        this.port = port;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!isLoopbackHost(request.getHeader("Host"))) {
            response.sendError(MISDIRECTED_REQUEST, "Invalid Host header");
            return;
        }
        if (!isLocalOrigin(request.getHeader("Origin"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Origin header");
            return;
        }
        if (!hasTheSecret(request.getHeader("Authorization"))) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"VSim\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or wrong secret");
            return;
        }
        if (request.getContentLengthLong() > McpServerSettings.MAXIMUM_REQUEST_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        if (stopping) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "VSim is turning the AI off");
            return;
        }
        if (!"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        serveWithinTheLimit(request, response, chain);
    }

    private void serveWithinTheLimit(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!requestSlots.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Too many requests at once");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            requestSlots.release();
        }
    }

    /**
     * The server is about to stop: new requests are turned away (503) and this waits, at most {@code grace}, until
     * the POSTs being served have written their answers, so closing the sessions never drops one (Pulido T1: the
     * AI's long poll, answered STOPPED by «Apagar», reached the client as an empty stream). True when none is left.
     */
    boolean drain(Duration grace) {
        stopping = true;
        try {
            return requestSlots.tryAcquire(McpServerSettings.MAXIMUM_CONCURRENT_REQUESTS, grace.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String suffix = ":" + port.getAsInt();
        return host.endsWith(suffix) && LOOPBACK_NAMES.contains(host.substring(0, host.length() - suffix.length()));
    }

    /** No Origin means not a browser (Claude Code sends none); a browser's must be a page on this machine. */
    private static boolean isLocalOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            URI uri = new URI(origin);
            String host = uri.getHost();
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && host != null
                    && (LOOPBACK_NAMES.contains(host) || LOOPBACK_NAMES.contains("[" + host + "]"));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /** Compared in constant time, so the answer's timing says nothing about the secret. */
    private boolean hasTheSecret(String authorization) {
        return authorization != null
                && MessageDigest.isEqual(expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8));
    }
}
