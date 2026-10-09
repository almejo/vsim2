package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.List;

/** One read of the library directory: the components that loaded and the files that did not. */
public record LibraryLoad(List<DefinitionBundle> bundles, List<LibraryProblem> problems) {

    public LibraryLoad {
        bundles = List.copyOf(bundles);
        problems = List.copyOf(problems);
    }
}
