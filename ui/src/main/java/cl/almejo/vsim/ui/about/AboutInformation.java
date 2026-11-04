package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.UIManager;
import java.util.Objects;

/**
 * What a bug report needs to know about this installation: the VSim version, the platform it runs on and how it was
 * installed (each package's launcher passes {@code -Dvsim.packageFormat}; absent means the plain jar).
 */
public record AboutInformation(String version, String javaVersion, String operatingSystem, String lookAndFeel,
                               String packageFormat) {

    public AboutInformation {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(javaVersion, "javaVersion");
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        Objects.requireNonNull(lookAndFeel, "lookAndFeel");
        Objects.requireNonNull(packageFormat, "packageFormat");
    }

    /** The running application's values. */
    public static AboutInformation current() {
        String java = System.getProperty("java.version", "?") + " (" + System.getProperty("java.vendor", "?") + ")";
        String system = System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "") + " "
                + System.getProperty("os.arch", "");
        return new AboutInformation(VsimVersion.current(), java, system.strip(),
                UIManager.getLookAndFeel().getName(), System.getProperty("vsim.packageFormat", "jar"));
    }

    /** The text «Copiar información» puts on the clipboard, one labelled line per value. */
    public String asText() {
        return Messages.format("about.info.version", version) + "\n"
                + Messages.format("about.info.java", javaVersion) + "\n"
                + Messages.format("about.info.system", operatingSystem) + "\n"
                + Messages.format("about.info.lookAndFeel", lookAndFeel) + "\n"
                + Messages.format("about.info.format", packageFormat);
    }
}
