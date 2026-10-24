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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Application-modal adapter: the working history dies with the dialog, accepting returns a single result. */
public final class SwingShapeEditor implements ShapeEditor {
    private final AppPreferences preferences;
    private final Supplier<ColorScheme> scheme;

    public SwingShapeEditor(AppPreferences preferences, Supplier<ColorScheme> scheme) {
        this.preferences = preferences;
        this.scheme = scheme;
    }

    @Override public Optional<ShapeEditResult> edit(Component owner, ShapeEditRequest request) {
        ShapeEditorPanel panel = new ShapeEditorPanel(request, preferences, scheme);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
                Messages.format("shape.title", request.definition().name()), Dialog.ModalityType.APPLICATION_MODAL);
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
            if (panel.canAccept()) { result.set(Optional.of(panel.result())); dialog.dispose(); }
        });
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close.run(); }
        });
        dialog.setContentPane(panel);
        dialog.setMinimumSize(new Dimension(840, 640));
        dialog.setBounds(preferences.shapeEditorBounds());
        dialog.getRootPane().setDefaultButton(panel.acceptButton());
        dialog.setVisible(true);
        preferences.setShapeEditorBounds(dialog.getBounds());
        return result.get();
    }
}
