package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.library.ComponentForm;
import cl.almejo.vsim.ui.library.LibraryDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** LibraryDialogs for the machine tests: every question is cancelled, and what is shown is recorded. */
final class QuietLibraryDialogs implements LibraryDialogs {

    final List<String> messages = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    @Override
    public Optional<ComponentForm.Result> editComponent(java.awt.Component parent, ComponentForm form) {
        return Optional.empty();
    }

    @Override
    public boolean confirmMissingPins(java.awt.Component parent, String message) {
        return false;
    }

    @Override
    public Optional<String> askNewName(java.awt.Component parent, String currentName) {
        return Optional.empty();
    }

    @Override
    public Optional<String> editDescription(java.awt.Component parent, String componentName, String current) {
        return Optional.empty();
    }

    @Override
    public Optional<Path> chooseExportFile(java.awt.Component parent, String suggestedFileName) {
        return Optional.empty();
    }

    @Override
    public List<Path> chooseImportFiles(java.awt.Component parent) {
        return List.of();
    }

    @Override
    public LibraryDialogs.ImportChoice askImportCollision(java.awt.Component parent, String componentName,
                                                        boolean canReplace) {
        return LibraryDialogs.ImportChoice.CANCEL;
    }

    @Override
    public boolean confirmRemove(java.awt.Component parent, String componentName) {
        return false;
    }

    @Override
    public void showInFolder(java.awt.Component parent, Path file) {
    }

    @Override
    public void showMessage(java.awt.Component parent, String message) {
        messages.add(message);
    }

    @Override
    public void showError(java.awt.Component parent, String message) {
        errors.add(message);
    }
}
