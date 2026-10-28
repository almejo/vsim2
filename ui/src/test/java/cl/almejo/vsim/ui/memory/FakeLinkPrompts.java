package cl.almejo.vsim.ui.memory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** {@link MemoryLinkPrompts} for tests: answers from fields a test sets and records what was asked and shown. */
final class FakeLinkPrompts implements MemoryLinkPrompts {

    Optional<Path> linkFile = Optional.empty();
    Optional<Path> memoryFile = Optional.empty();
    /** What the RAM/ROM question answers, given the proposal; empty cancels. */
    UnaryOperator<MemoryFileImport.Proposal> kindAnswer = proposal -> proposal;
    boolean cancelKind;
    final List<MemoryFileImport.Proposal> askedKinds = new ArrayList<>();
    final List<String> informed = new ArrayList<>();

    @Override
    public Optional<Path> chooseLinkFile(java.awt.Component parent) {
        return linkFile;
    }

    @Override
    public Optional<Path> chooseMemoryFile(java.awt.Component parent) {
        return memoryFile;
    }

    @Override
    public Optional<MemoryFileImport.Proposal> chooseKind(java.awt.Component parent, String fileName,
                                                          MemoryFileImport.Proposal proposal) {
        askedKinds.add(proposal);
        return cancelKind ? Optional.empty() : Optional.of(kindAnswer.apply(proposal));
    }

    @Override
    public void inform(java.awt.Component parent, String message) {
        informed.add(message);
    }
}
