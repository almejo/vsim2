package cl.almejo.vsim.mcp;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@code GET /vsim} answers {@code {"vsim":true}} without the secret, so a VSim that finds its port taken can tell
 * whether another VSim holds it (spec §9.1). It says nothing else about this VSim.
 */
final class VsimProbe extends HttpServlet {

    static final String PATH = "/vsim";
    static final String ANSWER = "{\"vsim\":true}";
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(ANSWER);
    }

    /** Whether a VSim server answers on {@code port} of this machine; false when nothing or something else does. */
    static boolean isVsimServer(int port) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH))
                .timeout(PROBE_TIMEOUT).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == HttpServletResponse.SC_OK && ANSWER.equals(response.body().strip());
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            client.close();
        }
    }
}
