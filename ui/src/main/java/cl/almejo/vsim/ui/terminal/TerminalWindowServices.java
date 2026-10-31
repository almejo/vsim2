package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.ui.about.ClipboardTarget;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What a Terminal window needs from the outside, all injectable: how it is shown, the clipboard it copies to and the
 * text a paste reads, the .txt chooser, where files are read and written ({@code files}, off the EDT) and how their
 * results come back ({@code edt}), how the screen is drawn and the clock of the bell's flash.
 */
public record TerminalWindowServices(TerminalWindowFactory factory, ClipboardTarget clipboard,
                                     Supplier<Optional<String>> clipboardText, TextFileChooser saveChooser,
                                     Executor files, Executor edt, TerminalScreenRenderer renderer,
                                     LongSupplier nanoClock) {

    public TerminalWindowServices {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(clipboardText, "clipboardText");
        Objects.requireNonNull(saveChooser, "saveChooser");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(edt, "edt");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(nanoClock, "nanoClock");
    }
}
