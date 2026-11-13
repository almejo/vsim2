package cl.almejo.vsim.ui.theme;

/** Installs the Swing look and feel and refreshes open windows. Injectable so tests never touch UIManager. */
@FunctionalInterface
public interface LookAndFeelInstaller {

    /** Installs the light or dark look and feel; true on success, false when it could not be installed. */
    boolean install(boolean dark);

    /**
     * Installs the light or dark look and feel at the interface's size and font; installers that know no sizes (test
     * fakes) install it as {@link #install(boolean)} does.
     */
    default boolean install(boolean dark, InterfaceLook look) {
        return install(dark);
    }
}
