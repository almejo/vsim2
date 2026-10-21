package cl.almejo.vsim.ui.ai;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MCP server works from the executable jar alone (A§10.2): the SDK finds its JSON mapper and schema validator
 * through the merged service files. Run with {@code ./gradlew :ui:executableJarTest}.
 */
@Tag("executable-jar")
class McpExecutableJarTest {

    private static final String SECRET = "a-secret-for-the-jar-test";

    private static Path jar() {
        String location = System.getProperty("vsim.executableJar", "");
        assertThat(location).as("run through ./gradlew :ui:executableJarTest").isNotBlank();
        return Path.of(location);
    }

    @Test
    void theMergedServiceFilesNameEveryProvider() throws IOException {
        try (JarFile jarFile = new JarFile(jar().toFile())) {
            assertThat(service(jarFile, "io.modelcontextprotocol.json.McpJsonMapperSupplier"))
                    .contains("io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier");
            assertThat(service(jarFile, "io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier"))
                    .contains("io.modelcontextprotocol.json.schema.jackson2.JacksonJsonSchemaValidatorSupplier");
            assertThat(service(jarFile, "org.slf4j.spi.SLF4JServiceProvider"))
                    .contains("ch.qos.logback.classic.spi.LogbackServiceProvider");
        }
    }

    @Test
    void anMcpClientListsTheDocumentsOfAServerRunFromTheJar() throws Exception {
        Process server = new ProcessBuilder(javaExecutable(), "-Djava.awt.headless=true", "-cp",
                jar() + File.pathSeparator + testClasses(), FatJarMcpServer.class.getName(), SECRET)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            int port = Integer.parseInt(new BufferedReader(new InputStreamReader(server.getInputStream(),
                    StandardCharsets.UTF_8)).readLine().strip());
            try (McpSyncClient client = client(port)) {
                client.initialize();
                McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                        .builder("vsim_list_documents").arguments(Map.of()).build());

                assertThat(result.structuredContent()).isEqualTo(Map.of("documents", List.of(Map.of(
                        "id", "circuit-1", "name", "Sumador", "version", 4, "modified", false))));
            }
        } finally {
            server.getOutputStream().close();
            if (!server.waitFor(10, TimeUnit.SECONDS)) {
                server.destroyForcibly();
            }
        }
        assertThat(server.exitValue()).isZero();
    }

    private static List<String> service(JarFile jarFile, String service) throws IOException {
        JarEntry entry = jarFile.getJarEntry("META-INF/services/" + service);
        assertThat(entry).as("META-INF/services/%s", service).isNotNull();
        try (InputStream input = jarFile.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip).toList();
        }
    }

    private static McpSyncClient client(int port) {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                        .endpoint("/mcp")
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + SECRET))
                        .build())
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command().orElseThrow();
    }

    /** Where {@link FatJarMcpServer} was compiled: the test classes, which hold no library class. */
    private static Path testClasses() throws Exception {
        Path location = Path.of(FatJarMcpServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(Files.isDirectory(location)).isTrue();
        return location;
    }
}
