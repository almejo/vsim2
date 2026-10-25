package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Application-modal adapter: the working history dies with the dialog, accepting returns a single result. */
public final class SwingShapeEditor implements ShapeEditor {
    /** The first size of the dialog: room for the canvas, the 270 px side panel and the preview cards. */
    private static final Dimension DEFAULT_SIZE = new Dimension(1080, 720);
    private static final Dimension MINIMUM_SIZE = new Dimension(840, 600);
    private final AppPreferences preferences;
    private final Supplier<ColorScheme> scheme;

    public SwingShapeEditor(AppPreferences preferences, Supplier<ColorScheme> scheme) {
        this.preferences = preferences;
        this.scheme = scheme;
    }

    @Override
    public Optional<ShapeEditResult> edit(Component owner, ShapeEditRequest request) {
        ShapeEditorPanel panel = new ShapeEditorPanel(request, preferences, scheme);
        Window ownerWindow = SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(ownerWindow, Messages.format("shape.title", request.definition().name()),
                Dialog.ModalityType.APPLICATION_MODAL);
        AtomicReference<Optional<ShapeEditResult>> result = new AtomicReference<>(Optional.empty());
        Runnable close = () -> {
            if (!panel.hasChanges() || JOptionPane.showConfirmDialog(dialog, Messages.text("shape.discard.message"),
                    Messages.text("shape.discard.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)
                    == JOptionPane.OK_OPTION) {
                dialog.dispose();
            }
        };
        panel.onClose(close);
        panel.onAccept(() -> {
            if (panel.canAccept()) {
                result.set(Optional.of(panel.result()));
                dialog.dispose();
            }
        });
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                close.run();
            }
        });
        dialog.setContentPane(panel);
        dialog.setMinimumSize(MINIMUM_SIZE);
        Rectangle ownerBounds = ownerWindow != null ? ownerWindow.getBounds() : owner.getBounds();
        dialog.setBounds(DialogPlacement.place(preferences.shapeEditorBounds(), ownerBounds, screens(),
                DEFAULT_SIZE));
        dialog.getRootPane().setDefaultButton(panel.acceptButton());
        dialog.setVisible(true);
        preferences.setShapeEditorBounds(dialog.getBounds());
        return result.get();
    }

    /** The usable bounds of every screen; none when headless. */
    private static List<Rectangle> screens() {
        if (GraphicsEnvironment.isHeadless()) {
            return List.of();
        }
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screens.add(device.getDefaultConfiguration().getBounds());
        }
        return screens;
    }
}
