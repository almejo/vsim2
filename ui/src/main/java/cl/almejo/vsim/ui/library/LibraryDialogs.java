package cl.almejo.vsim.ui.library;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Every question the templates and library features ask the user. Tests use a fake; the application the Swing one. */
public interface LibraryDialogs {

    enum ImportChoice { REPLACE, KEEP_BOTH, CANCEL }

    /** Shows the create/update dialog; empty when the user cancels. */
    Optional<ComponentForm.Result> editComponent(java.awt.Component parent, ComponentForm form);

    /**
     * Warns that the component being created lacks pins ({@code message}): true for «Crear igual», false for «Volver»
     * (back to the dialog).
     */
    boolean confirmMissingPins(java.awt.Component parent, String message);

    Optional<String> askNewName(java.awt.Component parent, String currentName);

    /**
     * «Editar descripción…» of the user component {@code componentName}, starting from {@code current}: the new text,
     * or empty when the user cancels.
     */
    Optional<String> editDescription(java.awt.Component parent, String componentName, String current);

    Optional<Path> chooseExportFile(java.awt.Component parent, String suggestedFileName);

    /** Empty when the user cancels. */
    List<Path> chooseImportFiles(java.awt.Component parent);

    ImportChoice askImportCollision(java.awt.Component parent, String componentName);

    boolean confirmRemove(java.awt.Component parent, String componentName);

    void showInFolder(java.awt.Component parent, Path file);

    void showMessage(java.awt.Component parent, String message);

    void showError(java.awt.Component parent, String message);
}
