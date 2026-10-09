package cl.almejo.vsim.ui.theme;

/** Answers whether the operating system is set to a dark appearance. */
@FunctionalInterface
public interface DarkModeProbe {

    boolean prefersDark();
}
