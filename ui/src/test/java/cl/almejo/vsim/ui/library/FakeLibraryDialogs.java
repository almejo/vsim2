package cl.almejo.vsim.ui.library;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** LibraryDialogs for tests: answers from queues (an empty queue cancels) and records everything shown. */
final class FakeLibraryDialogs implements LibraryDialogs {

    final Deque<Optional<Consumer<ComponentForm>>> formAnswers = new ArrayDeque<>();
    final List<ComponentForm> forms = new ArrayList<>();
    final Deque<Optional<String>> names = new ArrayDeque<>();
    final Deque<Optional<Path>> exportTargets = new ArrayDeque<>();
    final Deque<List<Path>> importFiles = new ArrayDeque<>();
    final Deque<ImportChoice> collisions = new ArrayDeque<>();
    final List<String> collisionNames = new ArrayList<>();
    final Deque<Boolean> confirmations = new ArrayDeque<>();
    final List<String> messages = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<Path> shownInFolder = new ArrayList<>();

    /** The next form is edited by {@code edit} and accepted when it is valid. */
    FakeLibraryDialogs acceptForm(Consumer<ComponentForm> edit) {
        formAnswers.add(Optional.of(edit));
        return this;
    }

    FakeLibraryDialogs cancelForm() {
        formAnswers.add(Optional.empty());
        return this;
    }

    @Override
    public Optional<ComponentForm.Result> editComponent(java.awt.Component parent, ComponentForm form) {
        forms.add(form);
        Optional<Consumer<ComponentForm>> answer = formAnswers.isEmpty() ? Optional.empty() : formAnswers.removeFirst();
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        answer.get().accept(form);
        return form.canAccept() ? Optional.of(form.result()) : Optional.empty();
    }

    @Override
    public Optional<String> askNewName(java.awt.Component parent, String currentName) {
        return names.isEmpty() ? Optional.empty() : names.removeFirst();
    }

    @Override
    public Optional<Path> chooseExportFile(java.awt.Component parent, String suggestedFileName) {
        return exportTargets.isEmpty() ? Optional.empty() : exportTargets.removeFirst();
    }

    @Override
    public List<Path> chooseImportFiles(java.awt.Component parent) {
        return importFiles.isEmpty() ? List.of() : importFiles.removeFirst();
    }

    @Override
    public ImportChoice askImportCollision(java.awt.Component parent, String componentName) {
        collisionNames.add(componentName);
        return collisions.isEmpty() ? ImportChoice.CANCEL : collisions.removeFirst();
    }

    @Override
    public boolean confirmRemove(java.awt.Component parent, String componentName) {
        return !confirmations.isEmpty() && confirmations.removeFirst();
    }

    @Override
    public void showInFolder(java.awt.Component parent, Path file) {
        shownInFolder.add(file);
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
