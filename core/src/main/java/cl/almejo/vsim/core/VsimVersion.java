package cl.almejo.vsim.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The version of VSim 2 this build is, from the {@code version.properties} resource the build writes. */
public final class VsimVersion {

    /** What {@link #current()} says when the resource is missing (a build outside Gradle). */
    public static final String UNKNOWN = "unknown";

    private static final String CURRENT = load();

    private VsimVersion() {
    }

    /** The app version, e.g. {@code 0.1}. */
    public static String current() {
        return CURRENT;
    }

    private static String load() {
        try (InputStream stream = VsimVersion.class.getResourceAsStream("version.properties")) {
            if (stream == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version", "").strip();
            return version.isEmpty() ? UNKNOWN : version;
        } catch (IOException exception) {
            return UNKNOWN;
        }
    }
}
