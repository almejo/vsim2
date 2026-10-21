package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Run by {@code McpExecutableJarTest} in its own JVM with the executable jar first on the class path: starts the MCP
 * server over one fixed circuit on a free port, prints the port, and stops when its standard input closes. Only VSim
 * classes are named here, so every library class (SDK, Jetty, Jackson, service files) comes from the jar.
 */
public final class FatJarMcpServer {

    static final DocumentSummary DOCUMENT = new DocumentSummary(new DocumentId("circuit-1"), "Sumador",
            new DocumentVersion(4), false);

    private FatJarMcpServer() {
    }

    public static void main(String[] arguments) throws Exception {
        RunningServer server = VsimMcpServer.start(new AutomationApi(new OneDocument()),
                new McpServerSettings(0, arguments[0]));
        System.out.println(server.port());
        System.out.flush();
        waitForInputToClose();
        server.stop();
    }

    private static void waitForInputToClose() throws IOException {
        while (System.in.read() >= 0) {
            // the test closes the stream to stop the server
        }
    }

    private static final class OneDocument implements AutomationHost {
        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.completedFuture(work.apply(() -> List.of(DOCUMENT)));
        }
    }
}
