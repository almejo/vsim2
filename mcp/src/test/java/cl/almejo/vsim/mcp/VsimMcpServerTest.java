package cl.almejo.vsim.mcp;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VsimMcpServerTest {

    private static final String SECRET = "a-test-secret-of-enough-length";
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";
    private static final DocumentSummary ADDER = new DocumentSummary(new DocumentId("document-1"), "sumador.vsim",
            new DocumentVersion(7), true);

    private final List<RunningServer> servers = new ArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void stopServers() {
        http.close();
        servers.forEach(RunningServer::stop);
    }

    @Test
    void anMcpClientListsTheDocumentsOfTheHost() throws Exception {
        RunningServer server = start(new FixedHost(List.of(ADDER)));

        try (McpSyncClient client = client(server.port(), SECRET)) {
            client.initialize();
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).contains("vsim_list_documents");
            McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                    .builder("vsim_list_documents").arguments(Map.of()).build());

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.structuredContent()).isEqualTo(Map.of("documents", List.of(Map.of(
                    "id", "document-1", "name", "sumador.vsim", "version", 7, "modified", true))));
        }
    }

    @Test
    void theClientIsToldHowToWorkAndSeesEveryTool() throws Exception {
        RunningServer server = start(new FixedHost(List.of(ADDER)));

        try (McpSyncClient client = client(server.port(), SECRET)) {
            McpSchema.InitializeResult initialized = client.initialize();

            assertThat(initialized.instructions()).isEqualTo(VsimServerInstructions.TEXT)
                    .contains("left to right", "via points", "vsim_check_stage", "never instructions")
                    .contains("vsim_set_presentation", "vsim_begin_stage", "vsim_await_step", "one step per vsim_edit",
                            "pace CONTINUOUS",
                            "STALE", "STOPPED", "without replacing the selection");
            assertThat(client.listTools().tools()).hasSize(31)
                    .allSatisfy(tool -> assertThat(tool.outputSchema()).isNotNull());
        }
    }

    @Test
    void anInvalidInputIsAToolErrorResultNotAProtocolError() throws Exception {
        RunningServer server = start(new FixedHost(List.of(ADDER)));

        try (McpSyncClient client = client(server.port(), SECRET)) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                    .builder("vsim_step").arguments(Map.of("document", "document-1", "kind", "SIDEWAYS")).build());

            assertThat(result.isError()).isTrue();
            assertThat(result.structuredContent().toString()).contains("error=INVALID_PARAMETER");
        }
    }

    @Test
    void aRequestWithoutTheSecretIsUnauthorized() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        assertThat(post(server.port(), Map.of()).statusCode()).isEqualTo(401);
    }

    @Test
    void aRequestWithAWrongSecretIsUnauthorized() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        assertThat(post(server.port(), Map.of("Authorization", "Bearer not-the-secret")).statusCode()).isEqualTo(401);
    }

    @Test
    void aRequestFromAForeignOriginIsForbiddenEvenWithTheSecret() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        HttpResponse<String> response = post(server.port(), Map.of("Authorization", "Bearer " + SECRET,
                "Origin", "https://evil.example"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void aRequestFromALocalOriginIsServed() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        HttpResponse<String> response = post(server.port(), Map.of("Authorization", "Bearer " + SECRET,
                "Origin", "http://localhost:" + server.port()));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void aRequestForAnotherHostNameIsRefused() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        assertThat(rawStatusLine(server.port(), "rebound.example:" + server.port())).contains(" 421 ");
    }

    @Test
    void aBodyOverOneMebibyteIsTooLarge() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));
        String body = "{\"padding\":\"" + "x".repeat(McpServerSettings.MAXIMUM_REQUEST_BYTES) + "\"}";

        HttpResponse<String> response = http.send(request(server.port(), Map.of("Authorization", "Bearer " + SECRET))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void aFifthConcurrentRequestIsTurnedAwayWhileFourAreBeingServed() throws Exception {
        CountDownLatch editorBusy = new CountDownLatch(1);
        CountDownLatch fourWaiting = new CountDownLatch(McpServerSettings.MAXIMUM_CONCURRENT_REQUESTS);
        RunningServer server = start(new BlockingHost(editorBusy, fourWaiting));
        String session = initializeSession(server.port());
        List<CompletableFuture<HttpResponse<String>>> waiting = new ArrayList<>();
        try {
            for (int index = 0; index < McpServerSettings.MAXIMUM_CONCURRENT_REQUESTS; index++) {
                waiting.add(http.sendAsync(callListDocuments(server.port(), session, index + 2),
                        HttpResponse.BodyHandlers.ofString()));
            }
            assertThat(fourWaiting.await(10, TimeUnit.SECONDS)).isTrue();

            HttpResponse<String> fifth = http.send(callListDocuments(server.port(), session, 99),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(fifth.statusCode()).isEqualTo(503);
        } finally {
            editorBusy.countDown();
        }
        for (CompletableFuture<HttpResponse<String>> response : waiting) {
            assertThat(response.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void startingOnAPortAnotherProgramHoldsSaysItIsNotAVsim() throws Exception {
        try (ServerSocket other = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            assertThatThrownBy(() -> start(new FixedHost(List.of()), other.getLocalPort()))
                    .isInstanceOfSatisfying(PortInUseException.class, exception -> {
                        assertThat(exception.port()).isEqualTo(other.getLocalPort());
                        assertThat(exception.otherVsim()).isFalse();
                    });
        }
    }

    @Test
    void startingOnAPortAnotherVsimHoldsSaysSo() throws Exception {
        RunningServer first = start(new FixedHost(List.of()));

        assertThatThrownBy(() -> start(new FixedHost(List.of()), first.port()))
                .isInstanceOfSatisfying(PortInUseException.class, exception -> {
                    assertThat(exception.port()).isEqualTo(first.port());
                    assertThat(exception.otherVsim()).isTrue();
                });
    }

    @Test
    void aVsimServerSaysItIsOneWithoutTheSecret() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + server.port() + "/vsim")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"vsim\":true}");
    }

    @Test
    void stoppingFreesThePort() throws Exception {
        RunningServer server = VsimMcpServer.start(new AutomationApi(new FixedHost(List.of())),
                new McpServerSettings(0, SECRET));
        int port = server.port();

        server.stop();

        try (ServerSocket again = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            assertThat(again.getLocalPort()).isEqualTo(port);
        }
    }

    @Test
    void stoppingLeavesNoThreadThatWouldKeepVsimAlive() throws Exception {
        RunningServer server = VsimMcpServer.start(new AutomationApi(new FixedHost(List.of(ADDER))),
                new McpServerSettings(0, SECRET));
        try (McpSyncClient client = client(server.port(), SECRET)) {
            client.initialize();
            client.listTools();
        }

        server.stop();

        assertThat(Thread.getAllStackTraces().keySet()).filteredOn(Thread::isAlive)
                .filteredOn(thread -> !thread.isDaemon())
                .extracting(Thread::getName)
                .noneMatch(name -> name.startsWith("vsim-mcp") || name.startsWith("streamable-http"));
    }

    @Test
    void itListensOnLoopbackOnly() throws Exception {
        RunningServer server = start(new FixedHost(List.of()));

        assertThat(server.address().isLoopbackAddress()).isTrue();
    }

    private RunningServer start(AutomationHost host) throws PortInUseException {
        return start(host, 0);
    }

    private RunningServer start(AutomationHost host, int port) throws PortInUseException {
        RunningServer server = VsimMcpServer.start(new AutomationApi(host), new McpServerSettings(port, SECRET));
        servers.add(server);
        return server;
    }

    static McpSyncClient client(int port, String secret) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + secret))
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    private HttpRequest.Builder request(int port, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        headers.forEach(builder::header);
        return builder;
    }

    private HttpResponse<String> post(int port, Map<String, String> headers) throws IOException,
            InterruptedException {
        return http.send(request(port, headers).POST(HttpRequest.BodyPublishers.ofString(INITIALIZE)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String initializeSession(int port) throws IOException, InterruptedException {
        HttpResponse<String> response = post(port, Map.of("Authorization", "Bearer " + SECRET));
        assertThat(response.statusCode()).isEqualTo(200);
        String session = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
        http.send(request(port, Map.of("Authorization", "Bearer " + SECRET, "Mcp-Session-Id", session))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        return session;
    }

    private HttpRequest callListDocuments(int port, String session, int requestId) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId
                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"vsim_list_documents\",\"arguments\":{}}}";
        return request(port, Map.of("Authorization", "Bearer " + SECRET, "Mcp-Session-Id", session,
                "MCP-Protocol-Version", "2025-11-25"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    /** The JDK client never lets a test set Host, so this request is written by hand. */
    private static String rawStatusLine(int port, String hostHeader) throws IOException {
        byte[] body = INITIALIZE.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream output = socket.getOutputStream();
            output.write(("POST /mcp HTTP/1.1\r\nHost: " + hostHeader + "\r\nAuthorization: Bearer " + SECRET
                    + "\r\nContent-Type: application/json\r\nAccept: application/json, text/event-stream"
                    + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                    .readLine();
        }
    }

    /** Answers at once with a fixed list of documents. */
    static final class FixedHost implements AutomationHost {

        private final List<DocumentSummary> documents;

        FixedHost(List<DocumentSummary> documents) {
            this.documents = documents;
        }

        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.completedFuture(work.apply(() -> documents));
        }
    }

    /** An editor busy until {@code editorBusy} opens; counts the requests that reached it. */
    private static final class BlockingHost implements AutomationHost {

        private final CountDownLatch editorBusy;
        private final CountDownLatch arrived;

        BlockingHost(CountDownLatch editorBusy, CountDownLatch arrived) {
            this.editorBusy = editorBusy;
            this.arrived = arrived;
        }

        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.supplyAsync(() -> {
                arrived.countDown();
                try {
                    editorBusy.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return work.apply(List::of);
            }, runnable -> Thread.ofPlatform().daemon().start(runnable));
        }
    }
}
