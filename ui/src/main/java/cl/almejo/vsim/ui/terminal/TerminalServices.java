package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.ui.about.ClipboardTarget;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.sound.SoundSink;
import cl.almejo.vsim.ui.sound.SoundSinks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.KeyboardFocusManager;
import java.nio.file.InvalidPathException;
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
 * writes (off the EDT, in order), how work is left for later on the EDT (opening the windows of «Mostrar: en una
 * ventana» once the editor window exists, the question about a saved circuit's output file) and that question.
 */
public record TerminalServices(TerminalWindowServices windows, Supplier<FrameTicker> tickers, SoundSink sound,
                               Executor outputWriter, Executor later, TerminalOutputConfirmation outputConfirmation) {

    /** A frame every 16 ms, like the canvas and the simulation's frames. */
    private static final int FRAME_MILLISECONDS = 16;
    private static final String TEXT_EXTENSION = ".txt";
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalServices.class);

    public TerminalServices {
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(tickers, "tickers");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(outputWriter, "outputWriter");
        Objects.requireNonNull(later, "later");
        Objects.requireNonNull(outputConfirmation, "outputConfirmation");
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
                files, SwingUtilities::invokeLater, TerminalServices::confirmOutputFile);
    }

    /**
     * The save dialog of the .txt files: {@code suggested} is a name («pantalla.txt», in the default folder) or a whole
     * path; an existing file is replaced only after the user confirms, as in the other save dialogs.
     */
    private static Optional<Path> chooseTextFile(java.awt.Component parent, String suggested) {
        Path directory = FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
        String name = suggested;
        try {
            Path suggestedPath = Path.of(suggested);
            if (suggestedPath.getParent() != null) {
                directory = suggestedPath.getParent();
                name = suggestedPath.getFileName().toString();
            }
        } catch (InvalidPathException exception) {
            // a title that is not a path on this system («a:b» on Windows): the name alone, in the default folder
            LOGGER.debug("The suggested name {} is not a path", suggested, exception);
        }
        return FileDialogs.chooseSave(parent, directory, name,
                new FileNameExtensionFilter(Messages.text("terminal.window.txtFiles"), "txt"),
                path -> FileNames.withExtension(path, TEXT_EXTENSION));
    }

    /** «Este circuito guarda la salida de … ¿Grabar ahí?», with «No grabar» as the default answer. */
    private static boolean confirmOutputFile(String terminalName, String shownFile) {
        String record = Messages.text("terminal.output.confirm.record");
        String skip = Messages.text("terminal.output.confirm.skip");
        int answer = JOptionPane.showOptionDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .getActiveWindow(), Messages.format("terminal.output.confirm.message", terminalName, shownFile),
                Messages.text("terminal.output.confirm.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[]{record, skip}, skip);
        return answer == JOptionPane.YES_OPTION;
    }
}
