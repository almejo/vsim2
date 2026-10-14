package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.bundled.BundledExamples;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the jar built by {@code ./gradlew :ui:executableJar}; run with {@code ./gradlew :ui:executableJarTest}. */
@Tag("executable-jar")
class ExecutableJarTest {

    private static Path jar() {
        String location = System.getProperty("vsim.executableJar", "");
        assertThat(location).as("run through ./gradlew :ui:executableJarTest").isNotBlank();
        Path jar = Path.of(location);
        assertThat(Files.isRegularFile(jar)).as("%s exists", jar).isTrue();
        return jar;
    }

    private static List<String> entryNames() throws IOException {
        try (JarFile jarFile = new JarFile(jar().toFile())) {
            return Collections.list(jarFile.entries()).stream().map(JarEntry::getName).toList();
        }
    }

    @Test
    void theJarIsNamedAfterTheProject() {
        assertThat(jar().getFileName().toString()).matches("vsim2-.+\\.jar");
    }

    @Test
    void theManifestNamesTheMainClass() throws IOException {
        try (JarFile jarFile = new JarFile(jar().toFile())) {
            Attributes attributes = jarFile.getManifest().getMainAttributes();
            assertThat(attributes.getValue(Attributes.Name.MAIN_CLASS)).isEqualTo("cl.almejo.vsim.ui.app.Main");
            assertThat(attributes.getValue("Multi-Release")).isEqualTo("true");
        }
    }

    @Test
    void itCarriesTheBundlesTheCoreAndEveryLibrary() throws IOException {
        assertThat(entryNames()).contains(
                "i18n/messages_es.properties",
                "i18n/messages_en.properties",
                "cl/almejo/vsim/ui/app/Main.class",
                "cl/almejo/vsim/core/document/CircuitDocument.class",
                "com/formdev/flatlaf/FlatLaf.class",
                "com/formdev/flatlaf/themes/FlatMacDarkLaf.class",
                "net/miginfocom/swing/MigLayout.class",
                "org/jfree/svg/SVGGraphics2D.class",
                "com/fasterxml/jackson/databind/ObjectMapper.class",
                "org/slf4j/LoggerFactory.class",
                "ch/qos/logback/classic/Logger.class");
    }

    @Test
    void itCarriesTheLoggingProviderTheSvgIconSupportAndTheApplicationIcon() throws IOException {
        assertThat(entryNames()).contains(
                "META-INF/services/org.slf4j.spi.SLF4JServiceProvider",
                "com/formdev/flatlaf/extras/FlatSVGIcon.class",
                "icons/app/vsim.svg");
    }

    @Test
    void itCarriesTheBundledExamples() throws IOException {
        assertThat(entryNames()).containsAll(BundledExamples.all().stream()
                .map(example -> example.resourceName().substring(1)).toList());
    }

    @Test
    void hasNoSignatureFiles() throws IOException {
        assertThat(entryNames()).noneMatch(name -> name.matches("META-INF/[^/]+\\.(SF|DSA|RSA|EC)"));
    }

    @Test
    void theMainClassAndTheMacThemesLoadFromTheJarAlone() throws Exception {
        URL[] urls = {jar().toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> main = Class.forName("cl.almejo.vsim.ui.app.Main", false, loader);
            assertThat(main.getClassLoader()).isSameAs(loader);
            assertThat(main.getMethod("main", String[].class)).isNotNull();
            assertThat(Class.forName("com.formdev.flatlaf.themes.FlatMacDarkLaf", false, loader).getClassLoader())
                    .isSameAs(loader);
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH, loader,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            assertThat(bundle.getString("action.preferences")).isEqualTo("Preferences…");
        }
    }
}
