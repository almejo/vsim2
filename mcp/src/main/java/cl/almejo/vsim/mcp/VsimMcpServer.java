package cl.almejo.vsim.mcp;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.mcp.prompts.HelpResource;
import cl.almejo.vsim.mcp.prompts.VsimPrompts;
import cl.almejo.vsim.mcp.tools.VsimTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The MCP server inside VSim (spec §9, §11): the official MCP Java SDK's Streamable HTTP transport in an embedded
 * Jetty, on loopback only, at {@code /mcp}, behind the {@link AccessFilter}. One per VSim process; the editor owns its
 * lifecycle.
 */
public final class VsimMcpServer {

    static final String MCP_PATH = "/mcp";
    private static final Logger LOGGER = LoggerFactory.getLogger(VsimMcpServer.class);
    private static final String SERVER_NAME = "vsim";
    /** Requests in flight get this long to finish when the server stops (VSim quitting, the user turning it off). */
    private static final Duration STOP_GRACE = Duration.ofSeconds(2);
    /** Jetty's own threads plus the requests: a handful is plenty for one client on this machine. */
    private static final int MAXIMUM_THREADS = 16;
    private static final int MINIMUM_THREADS = 2;

    private VsimMcpServer() {
    }

    /**
     * Starts listening on {@code 127.0.0.1:settings.port()}; when that port is taken, {@link PortInUseException} says
     * whether another VSim holds it. VSim never picks another port by itself (spec §9.1).
     */
    public static RunningServer start(AutomationApi api, McpServerSettings settings) throws PortInUseException {
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_PATH)
                .maxRequestSize(McpServerSettings.MAXIMUM_REQUEST_BYTES)
                // SDK 2.1.0: the idle-session sweeper runs on a non-daemon scheduler that stopping never disposes,
                // so each start would leave a thread behind. One client on this machine makes few sessions, and
                // stopping drops them all, so they are not swept.
                .sessionSweepInterval(null)
                .build();
        McpSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, VsimVersion.current())
                .jsonMapper(jsonMapper)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).prompts(false)
                        .resources(false, false).build())
                .instructions(VsimServerInstructions.TEXT)
                // Each tool checks its own input, so a bad one is answered with {error, message, recovery}.
                .validateToolInputs(false)
                .tools(VsimTools.specifications(api, jsonMapper))
                // IA.3: the commands that teach the AI (read-only texts; the tools and their rules stay the same).
                .prompts(VsimPrompts.specifications())
                .resources(HelpResource.specification())
                .build();
        Server jetty = new Server(threadPool());
        jetty.setStopTimeout(STOP_GRACE.toMillis());
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost(InetAddress.getLoopbackAddress().getHostAddress());
        connector.setPort(settings.port());
        jetty.addConnector(connector);
        jetty.setHandler(context(transport, new AccessFilter(settings.secret(), connector::getLocalPort)));
        startOrExplain(jetty, mcpServer, settings.port());
        return new Running(jetty, mcpServer, connector);
    }

    private static QueuedThreadPool threadPool() {
        QueuedThreadPool threadPool = new QueuedThreadPool(MAXIMUM_THREADS, MINIMUM_THREADS);
        threadPool.setName("vsim-mcp");
        // Never keeps VSim alive: quitting stops the server, and a crash must not leave a process listening.
        threadPool.setDaemon(true);
        return threadPool;
    }

    private static ServletContextHandler context(HttpServletStreamableServerTransportProvider transport,
                                                 AccessFilter accessFilter) {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transport), MCP_PATH);
        context.addServlet(new ServletHolder(new VsimProbe()), VsimProbe.PATH);
        context.addFilter(new FilterHolder(accessFilter), MCP_PATH, EnumSet.of(DispatcherType.REQUEST));
        return context;
    }

    private static void startOrExplain(Server jetty, McpSyncServer mcpServer, int port) throws PortInUseException {
        try {
            jetty.start();
        } catch (Exception exception) {
            stopQuietly(jetty, mcpServer);
            if (causedByBind(exception)) {
                throw new PortInUseException(port, VsimProbe.isVsimServer(port), exception);
            }
            throw new IllegalStateException("The MCP server could not start", exception);
        }
    }

    private static boolean causedByBind(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BindException) {
                return true;
            }
        }
        return false;
    }

    private static void stopQuietly(Server jetty, McpSyncServer mcpServer) {
        try {
            mcpServer.closeGracefully();
        } catch (RuntimeException exception) {
            LOGGER.warn("The MCP sessions did not close cleanly", exception);
        }
        try {
            jetty.stop();
        } catch (Exception exception) {
            LOGGER.warn("The MCP server did not stop cleanly", exception);
        }
    }

    private static final class Running implements RunningServer {

        private final Server jetty;
        private final McpSyncServer mcpServer;
        private final InetSocketAddress boundAddress;
        private final AtomicBoolean stopped = new AtomicBoolean();

        Running(Server jetty, McpSyncServer mcpServer, ServerConnector connector) {
            this.jetty = jetty;
            this.mcpServer = mcpServer;
            this.boundAddress = boundAddress(connector);
        }

        private static InetSocketAddress boundAddress(ServerConnector connector) {
            try {
                return (InetSocketAddress) ((ServerSocketChannel) connector.getTransport()).getLocalAddress();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public int port() {
            return boundAddress.getPort();
        }

        @Override
        public InetAddress address() {
            return boundAddress.getAddress();
        }

        @Override
        public void stop() {
            if (stopped.compareAndSet(false, true)) {
                stopQuietly(jetty, mcpServer);
            }
        }
    }
}
