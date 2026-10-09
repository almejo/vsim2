package cl.almejo.vsim.ui.actions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** TextPrompt for tests: answers from a queue (empty = cancelled) and remembers the initial texts. */
final class FakePrompt implements TextPrompt {

    final Deque<Optional<String>> answers = new ArrayDeque<>();
    final List<String> initialTexts = new ArrayList<>();

    FakePrompt answer(String text) {
        answers.add(Optional.of(text));
        return this;
    }

    FakePrompt cancel() {
        answers.add(Optional.empty());
        return this;
    }

    @Override
    public Optional<String> ask(java.awt.Component parent, String title, String message, String initialText) {
        initialTexts.add(initialText);
        return answers.isEmpty() ? Optional.empty() : answers.removeFirst();
    }
}
