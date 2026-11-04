package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The files under {@code packaging/} that give VSim its identity on the desktop (Distribución, Global constraints):
 * the desktop entry, jpackage's overrides for the deb and rpm, the {@code .vsim} file association and the icons.
 */
class PackagingFilesTest {

    private static final String APPLICATION_ID = "io.github.almejo.vsim2";
    /** The unnumbered PNG jpackage and the desktop take: 512 pixels, what Flathub and the deb's menu ask at most. */
    private static final int LARGEST_PNG_ICON = 512;
    private static final String MIME_TYPE = "application/x-vsim2";
    /** What X11 and Wayland report as the window class of a Swing window: the main class with dashes for dots. */
    private static final String WINDOW_CLASS = Main.class.getName().replace('.', '-');

    /** The repository's {@code packaging/} folder: from Gradle's system property, else next to the ui module. */
    private static Path packaging() {
        Path folder = Path.of(System.getProperty("vsim.packagingDirectory", "../packaging"));
        assertThat(folder).isDirectory();
        return folder;
    }

    /** The {@code key=value} lines of a desktop entry's {@code [Desktop Entry]} group. */
    private static Map<String, String> desktopEntry(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).first().isEqualTo("[Desktop Entry]");
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            int equals = line.indexOf('=');
            if (equals > 0 && !line.startsWith("#")) {
                entries.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return entries;
    }

    private static List<String> listValue(String value) {
        return Arrays.stream(value.split(";")).filter(part -> !part.isEmpty()).toList();
    }

    @Test
    void theWindowClassIsTheOneTheThrowawayAppImageProved() {
        assertThat(WINDOW_CLASS).isEqualTo("cl-almejo-vsim-ui-app-Main");
    }

    @Test
    void theDesktopEntryCarriesTheApplicationsIdentity() throws IOException {
        Map<String, String> entry = desktopEntry(packaging().resolve("linux/" + APPLICATION_ID + ".desktop"));

        assertThat(entry).containsEntry("Type", "Application")
                .containsEntry("Name", "VSim 2")
                .containsEntry("Icon", APPLICATION_ID)
                .containsEntry("StartupWMClass", WINDOW_CLASS)
                .containsEntry("Terminal", "false");
        assertThat(entry.get("Exec")).endsWith(" %F");
        assertThat(listValue(entry.get("MimeType"))).containsExactly(MIME_TYPE);
        assertThat(listValue(entry.get("Categories"))).contains("Education");
    }

    @Test
    void theDebAndRpmDesktopEntryKeepsJpackagesPlaceholdersAndAddsTheWindowClass() throws IOException {
        Map<String, String> entry = desktopEntry(packaging().resolve("linux/jpackage/VSim2.desktop"));

        assertThat(entry).containsEntry("Exec", "APPLICATION_LAUNCHER %F")
                .containsEntry("Icon", "APPLICATION_ICON")
                .containsEntry("StartupWMClass", WINDOW_CLASS)
                .containsEntry("Name", "VSim 2");
        assertThat(Files.readString(packaging().resolve("linux/jpackage/VSim2.desktop"))).contains("DESKTOP_MIMES");
    }

    @Test
    void theDebCreatesTheMenuDirectoryJpackagesPostinstNeeds() throws IOException {
        String preinst = Files.readString(packaging().resolve("linux/jpackage/preinst"));

        assertThat(preinst).startsWith("#!/bin/sh").contains("mkdir -p /usr/share/desktop-directories");
    }

    @Test
    void vsimFilesAreAssociatedWithVsim() throws IOException {
        Properties association = new Properties();
        try (Reader reader = Files.newBufferedReader(packaging().resolve("file-associations/vsim.properties"))) {
            association.load(reader);
        }

        assertThat(association.getProperty("extension")).isEqualTo("vsim");
        assertThat(association.getProperty("mime-type")).isEqualTo(MIME_TYPE);
        assertThat(association.getProperty("description")).isNotBlank();
    }

    @Test
    void theMetainfoNamesTheDesktopEntryAndTheLicence() throws Exception {
        Document metainfo = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(packaging().resolve("linux/" + APPLICATION_ID + ".metainfo.xml").toFile());
        Element component = metainfo.getDocumentElement();

        assertThat(component.getAttribute("type")).isEqualTo("desktop-application");
        assertThat(component.getElementsByTagName("id").item(0).getTextContent()).isEqualTo(APPLICATION_ID);
        assertThat(component.getElementsByTagName("launchable").item(0).getTextContent())
                .isEqualTo(APPLICATION_ID + ".desktop");
        assertThat(component.getElementsByTagName("project_license").item(0).getTextContent())
                .isEqualTo("GPL-3.0-or-later");
    }

    private static void assertSquare(Path png, int size) throws IOException {
        BufferedImage icon = ImageIO.read(png.toFile());
        assertThat(icon).as("%s is a PNG", png).isNotNull();
        assertThat(icon.getWidth()).as("width of %s", png).isEqualTo(size);
        assertThat(icon.getHeight()).as("height of %s", png).isEqualTo(size);
    }

    @Test
    void theIconsHaveEverySizeTheFormatsAskFor() throws IOException {
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            assertSquare(packaging().resolve("icons/" + APPLICATION_ID + "-" + size + ".png"), size);
        }
        assertSquare(packaging().resolve("icons/" + APPLICATION_ID + ".png"), LARGEST_PNG_ICON);
        assertThat(packaging().resolve("icons/" + APPLICATION_ID + ".ico")).isRegularFile();
        assertThat(packaging().resolve("icons/" + APPLICATION_ID + ".icns")).isRegularFile();
    }
}
