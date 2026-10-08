package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.List;

public final class CompositeCommand implements Command {

    private final String description;
    private final List<Command> commands;

    public CompositeCommand(String description, List<Command> commands) {
        this.description = description;
        this.commands = List.copyOf(commands);
    }

    @Override
    public void apply(CircuitDocument document) {
        document.batch(() -> commands.forEach(command -> command.apply(document)));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            for (int index = commands.size() - 1; index >= 0; index--) {
                commands.get(index).revert(document);
            }
        });
    }

    @Override
    public String description() {
        return description;
    }
}
