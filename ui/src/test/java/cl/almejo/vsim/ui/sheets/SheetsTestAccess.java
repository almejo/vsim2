package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.print.PrintedSheets;
import cl.almejo.vsim.ui.print.SheetGrid;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Optional;

/** Lets the tests of other packages read the sheets a view's canvas draws, as its own picture has them. */
public final class SheetsTestAccess implements AutoCloseable {

    private final SheetsFeatures features;

    private SheetsTestAccess(SheetsFeatures features) {
        this.features = features;
    }

    /** The sheets feature installed on {@code view}, sharing {@code anchors}. */
    public static SheetsTestAccess install(SheetAnchors anchors, EditorView view) {
        return new SheetsTestAccess(new SheetsExtension(anchors, (menu, invoker, x, y) -> { }).installFeatures(view));
    }

    /** The grid of the canvas's cached picture; empty while hidden. */
    public Optional<SheetGrid> canvasGrid() {
        return features.picture().map(SheetPicture::grid);
    }

    /** Which sheets of the canvas's cached picture go out (the others are drawn as empty); empty while hidden. */
    public Optional<PrintedSheets> canvasPrintedSheets() {
        return features.picture().map(SheetPicture::printed);
    }

    @Override
    public void close() {
        features.dispose();
    }
}
