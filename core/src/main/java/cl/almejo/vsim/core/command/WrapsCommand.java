package cl.almejo.vsim.core.command;

/**
 * A command that carries another one out and adds to it (an edit inside a component writes its body back, the editor
 * marks the pins a command loosened): whoever looks in the history for the command a caller made ({@code AiEditCommand},
 * for «Deshacer lo que hizo la IA») sees through the wrappers with {@link #unwrapped}.
 */
public interface WrapsCommand extends Command {

    /** The command this one carries out. */
    Command wrapped();

    /** {@code command} with every wrapper taken off. */
    static Command unwrapped(Command command) {
        Command current = command;
        while (current instanceof WrapsCommand wrapper) {
            current = wrapper.wrapped();
        }
        return current;
    }
}
