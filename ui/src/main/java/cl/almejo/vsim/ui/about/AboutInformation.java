package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.UIManager;
import java.util.Objects;

/** What a bug report needs to know about this installation: the VSim version and the platform it runs on. */
public record AboutInformation(String version, String javaVersion, String operatingSystem, String lookAndFeel) {

    public AboutInformation {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(javaVersion, "javaVersion");
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        Objects.requireNonNull(lookAndFeel, "lookAndFeel");
    }

    /** The running application's values. */
    public static AboutInformation current() {
        String java = System.getProperty("java.version", "?") + " (" + System.getProperty("java.vendor", "?") + ")";
        String system = System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "") + " "
                + System.getProperty("os.arch", "");
        return new AboutInformation(VsimVersion.current(), java, system.strip(),
                UIManager.getLookAndFeel().getName());
    }

    /** The text «Copiar información» puts on the clipboard, one labelled line per value. */
    public String asText() {
        return Messages.format("about.info.version", version) + "\n"
                + Messages.format("about.info.java", javaVersion) + "\n"
                + Messages.format("about.info.system", operatingSystem) + "\n"
                + Messages.format("about.info.lookAndFeel", lookAndFeel);
    }
}
