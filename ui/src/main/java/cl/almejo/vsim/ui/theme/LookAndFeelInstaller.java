package cl.almejo.vsim.ui.theme;

/** Installs the Swing look and feel and refreshes open windows. Injectable so tests never touch UIManager. */
@FunctionalInterface
public interface LookAndFeelInstaller {

    void install(boolean dark);
}
