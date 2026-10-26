package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import javax.swing.JComponent;
import java.awt.geom.AffineTransform;

/**
 * Where an {@link InlineTextEditor} opens: a drawing surface that can hold the editor over it, tells when it moves
 * or zooms and maps the document pixels the request speaks in to its own pixels. The circuit canvas is one, the
 * shape editor's canvas another.
 */
public interface InlineHost {

    /** The component the editor floats over: it gets the focus back when the editor closes. */
    JComponent component();

    /** Puts {@code editor} over the surface; returns what takes it away. */
    Runnable addFloatingComponent(JComponent editor);

    /** Runs {@code listener} when the surface pans or zooms; returns its removal. */
    Runnable addViewportListener(Runnable listener);

    /** Runs {@code listener} after every change to what is being edited; returns its removal. */
    Runnable addChangeListener(Runnable listener);

    /** Document pixels (100 %) to the surface's pixels. */
    AffineTransform documentTransform();

    /** How much the surface magnifies the document pixels. */
    double zoom();

    /** The circuit canvas as a host. */
    static InlineHost of(CircuitCanvas canvas) {
        return new CanvasHost(canvas);
    }

    /** The adapter of a {@link CircuitCanvas}. */
    final class CanvasHost implements InlineHost {
        private final CircuitCanvas canvas;

        private CanvasHost(CircuitCanvas canvas) {
            this.canvas = canvas;
        }

        /** The adapted canvas. */
        public CircuitCanvas canvas() {
            return canvas;
        }

        @Override
        public JComponent component() {
            return canvas;
        }

        @Override
        public Runnable addFloatingComponent(JComponent editor) {
            return canvas.addFloatingComponent(editor);
        }

        @Override
        public Runnable addViewportListener(Runnable listener) {
            return canvas.addViewportListener(listener);
        }

        @Override
        public Runnable addChangeListener(Runnable listener) {
            CircuitDocument document = canvas.openDocument().document();
            DocumentListener documentListener = (changed, change) -> listener.run();
            document.addListener(documentListener);
            return () -> document.removeListener(documentListener);
        }

        @Override
        public AffineTransform documentTransform() {
            return canvas.viewport().documentTransform();
        }

        @Override
        public double zoom() {
            return canvas.viewport().zoom();
        }
    }
}
