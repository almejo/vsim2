package cl.almejo.vsim.ui.about;

import java.awt.Component;

/** Shows «Acerca de VSim 2…». Injectable so tests never open a window. */
@FunctionalInterface
public interface AboutPresenter {

    /** Shows the dialog over {@code owner}'s window. */
    void show(Component owner);

    /** The real dialog, copying to the system clipboard and opening links in the browser. */
    static AboutPresenter swing() {
        return new SwingAboutPresenter(AboutInformation::current, ClipboardTarget.system(), LinkOpener.desktop());
    }
}
