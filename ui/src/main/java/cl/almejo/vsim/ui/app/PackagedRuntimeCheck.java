package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.PdfPages;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ServiceConfigurationError;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A headless check that a package's own Java runtime (the jlink'd one inside the deb, msi, dmg, AppImage, …) can run
 * VSim (Distribución, spec §3): it loads both message bundles and the Spanish language data, opens a bundled example
 * from Archivo ▸ Ejemplos, simulates it, exports SVG and PDF and makes one request to the AI server on a free loopback
 * port. A module missing from the runtime shows up here as a failing step, before a tester sees it.
 *
 * <p>Run with the package's own launcher runtime: {@code <runtime>/bin/java -cp <the jar>
 * cl.almejo.vsim.ui.app.PackagedRuntimeCheck}. Exit code 0 and the line {@value #SUCCESS_LINE} when every step
 * passes; otherwise exit code 1 and «PackagedRuntimeCheck FAILED at <step>: <cause>» on the standard error.
 */
public final class PackagedRuntimeCheck {

    static final String SUCCESS_LINE = "PackagedRuntimeCheck OK";
    static final String SVG_FILE_NAME = "circuit.svg";
    static final String PDF_FILE_NAME = "circuit.pdf";
    private static final int FAILURE_EXIT_CODE = 1;

    private PackagedRuntimeCheck() {
    }

    /** One named part of the check; the name is what the failure line says. */
    record Step(String name, Action action) {
        Step {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(action, "action");
        }
    }

    /** What a step does; anything it throws, errors included (a missing class), fails the check. */
    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    public static void main(String[] arguments) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path workDirectory = Files.createTempDirectory("vsim-packaged-runtime-check");
        int exitCode;
        try (Checks checks = new Checks(workDirectory)) {
            exitCode = run(checks.steps(), System.out, System.err);
        } finally {
            deleteRecursively(workDirectory);
        }
        System.exit(exitCode);
    }

    /**
     * Runs {@code steps} in order and stops at the first that throws. Returns the process exit code: 0 after printing
     * {@value #SUCCESS_LINE} on {@code output}, else 1 after naming the failing step and its stack trace on
     * {@code errors}.
     */
    static int run(List<Step> steps, PrintStream output, PrintStream errors) {
        for (Step step : steps) {
            try {
                step.action().run();
            } catch (Exception | LinkageError | ServiceConfigurationError failure) {
                errors.println("PackagedRuntimeCheck FAILED at " + step.name() + ": " + failure);
                failure.printStackTrace(errors);
                return FAILURE_EXIT_CODE;
            }
            output.println("passed: " + step.name());
        }
        output.println(SUCCESS_LINE);
        return 0;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** The real steps, which share the opened example; closing stops its simulation. */
    static final class Checks implements AutoCloseable {

        /** The example opened and simulated: the 4-bit adder, which also holds a user component definition. */
        private static final String EXAMPLE_ID = "adder-4-bits";
        /** Its switches set to 1111 + 0000 with carry in: the carry out lights up only after a real simulation. */
        private static final List<String> SWITCHES_TO_TURN_ON = List.of("A0", "A1", "A2", "A3", "Cin");
        private static final String CARRY_OUT_LABEL = "Cout";
        /** Far more than the adder needs even on a slow runner; a dead simulation fails instead of hanging. */
        private static final Duration SIMULATION_TIMEOUT = Duration.ofSeconds(30);
        private static final Duration POLL_INTERVAL = Duration.ofMillis(20);
        private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
        /** Any key both bundles hold. */
        private static final String KEY_IN_EVERY_BUNDLE = "action.preferences";
        /** Spanish month names come from the jdk.localedata module; without it «November» would appear. */
        private static final String SPANISH_NOVEMBER = "noviembre";
        private static final int SECRET_BYTES = 24;
        /** The MCP handshake a client sends first; the answer names the server. */
        private static final String INITIALIZE_REQUEST = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
                "capabilities":{},"clientInfo":{"name":"PackagedRuntimeCheck","version":"1"}}}""";

        private final Path workDirectory;
        private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
        private OpenDocument example;

        Checks(Path workDirectory) {
            this.workDirectory = Objects.requireNonNull(workDirectory, "workDirectory");
        }

        List<Step> steps() {
            return List.of(
                    new Step("message bundles and language data", this::loadBundlesAndLanguageData),
                    new Step("bundled example", this::openExample),
                    new Step("simulation", this::simulate),
                    new Step("SVG export", this::exportSvg),
                    new Step("PDF export", this::exportPdf),
                    new Step("AI server", this::askTheAiServer));
        }

        private void loadBundlesAndLanguageData() {
            for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
                ResourceBundle bundle = Messages.bundle(locale);
                require(bundle.getLocale().getLanguage().equals(locale.getLanguage()),
                        "the " + locale + " bundle loaded as " + bundle.getLocale());
                require(!bundle.getString(KEY_IN_EVERY_BUNDLE).isBlank(), KEY_IN_EVERY_BUNDLE + " is blank");
            }
            String november = Month.NOVEMBER.getDisplayName(TextStyle.FULL, Messages.SPANISH);
            require(SPANISH_NOVEMBER.equals(november), "Spanish month names are missing: got " + november);
        }

        private void openExample() throws Exception {
            example = new OpenDocument(BundledExamples.open(EXAMPLE_ID, registry), registry, null, false);
        }

        private void simulate() throws InterruptedException {
            OpenDocument document = openedExample();
            for (String label : SWITCHES_TO_TURN_ON) {
                document.interact(component(document, label).id());
            }
            long deadline = System.nanoTime() + SIMULATION_TIMEOUT.toNanos();
            while (!Value.ONE.equals(carryOut(document))) {
                require(System.nanoTime() < deadline, "the carry out never turned on");
                Thread.sleep(POLL_INTERVAL.toMillis());
            }
        }

        private void exportSvg() throws IOException {
            ImageExport.writeSvg(workDirectory.resolve(SVG_FILE_NAME), image());
            require(Files.readString(workDirectory.resolve(SVG_FILE_NAME)).contains("<svg"), "no SVG written");
        }

        private void exportPdf() throws IOException {
            CircuitImage image = image();
            Path target = workDirectory.resolve(PDF_FILE_NAME);
            try (OutputStream output = Files.newOutputStream(target)) {
                PdfPages.write(output, List.of(new PdfPages.PageSize(image.width(1), image.height(1))),
                        (pageIndex, graphics) -> image.paint(graphics, 1));
            }
            require(Files.size(target) > 0, "an empty PDF");
        }

        private void askTheAiServer() throws Exception {
            String secret = newSecret();
            RunningServer server = VsimMcpServer.start(new AutomationApi(new NoDocuments()),
                    new McpServerSettings(0, secret));
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()) {
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + server.port() + "/mcp"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + secret)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                require(response.statusCode() == 200, "the AI server answered " + response.statusCode());
                require(response.body().contains("\"serverInfo\""), "the AI server's answer has no serverInfo");
            } finally {
                server.stop();
            }
        }

        private OpenDocument openedExample() {
            require(example != null, "the example was not opened");
            return example;
        }

        private CircuitImage image() {
            OpenDocument document = openedExample();
            Optional<CircuitImage> image = CircuitImage.of(document.document(), document.netlist(),
                    document.netlistToken(), document.latestSnapshot(), document.registry(),
                    PainterRegistry.standard(), ColorScheme.LIGHT, Optional.empty());
            require(image.isPresent(), "the example has nothing to paint");
            return image.get();
        }

        private static Component component(OpenDocument document, String label) {
            return document.document().components().stream()
                    .filter(component -> component.label().equals(label))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the example has no component labeled " + label));
        }

        private static Value carryOut(OpenDocument document) {
            VisibleState state = document.latestSnapshot()
                    .componentState(component(document, CARRY_OUT_LABEL).id().value());
            return state instanceof VisibleState.LedState led ? led.value() : Value.HIGH_IMPEDANCE;
        }

        private static String newSecret() {
            byte[] bytes = new byte[SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        private static void require(boolean condition, String problem) {
            if (!condition) {
                throw new IllegalStateException(problem);
            }
        }

        @Override
        public void close() {
            if (example != null) {
                example.close();
            }
        }
    }

    /** The AI server's view of an editor with nothing open: the check only needs the server to answer. */
    private static final class NoDocuments implements AutomationHost {
        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.completedFuture(work.apply(List::of));
        }
    }
}
