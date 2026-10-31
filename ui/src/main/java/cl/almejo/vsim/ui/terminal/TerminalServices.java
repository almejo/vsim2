package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.ui.about.ClipboardTarget;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.sound.SoundSink;
import cl.almejo.vsim.ui.sound.SoundSinks;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Everything the Terminal features take from the outside, injectable for tests: the windows' services, a frame ticker
 * per document (the frames, the sounds and the bell's flash are polled on it), the sound sink, where «Guardar salida»
 * writes (off the EDT, in order) and how work is left for later on the EDT (opening the windows of «Mostrar: en una
 * ventana» once the editor window exists).
 */
public record TerminalServices(TerminalWindowServices windows, Supplier<FrameTicker> tickers, SoundSink sound,
                               Executor outputWriter, Executor later) {

    /** A frame every 16 ms, like the canvas and the simulation's frames. */
    private static final int FRAME_MILLISECONDS = 16;

    public TerminalServices {
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(tickers, "tickers");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(outputWriter, "outputWriter");
        Objects.requireNonNull(later, "later");
    }

    /** The editor's: real dialogs, the system clipboard, Swing timers, sound unless headless, one writer thread. */
    public static TerminalServices standard() {
        ExecutorService files = Executors.newSingleThreadExecutor(work -> {
            Thread thread = new Thread(work, "vsim-terminal-files");
            thread.setDaemon(true);
            return thread;
        });
        TerminalWindowServices windows = new TerminalWindowServices(new DialogTerminalWindowFactory(),
                ClipboardTarget.system(), () -> FragmentClipboard.system().readPlainText(),
                TerminalServices::chooseTextFile, files, SwingUtilities::invokeLater, TerminalScreenRenderer.standard(),
                System::nanoTime);
        return new TerminalServices(windows, () -> FrameTicker.swing(FRAME_MILLISECONDS), SoundSinks.forEnvironment(),
                files, SwingUtilities::invokeLater);
    }

    private static Optional<Path> chooseTextFile(java.awt.Component parent, String suggestedName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(Messages.text("terminal.window.txtFiles"), "txt"));
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        Path chosen = chooser.getSelectedFile().toPath();
        String name = chosen.getFileName().toString();
        return Optional.of(name.contains(".") ? chosen : chosen.resolveSibling(name + ".txt"));
    }
}
