package cl.almejo.vsim.ui.theme;

/** Installs the Swing look and feel and refreshes open windows. Injectable so tests never touch UIManager. */
@FunctionalInterface
public interface LookAndFeelInstaller {

    /** Installs the light or dark look and feel; true on success, false when it could not be installed. */
    boolean install(boolean dark);
}
