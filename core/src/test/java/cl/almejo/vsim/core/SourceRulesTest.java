package cl.almejo.vsim.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source rules that PMD cannot express, checked over the {@code src/main/java} trees of the four modules. The
 * rest of the automatic rules live in {@code config/pmd/vsim-rules.xml} (PMD) and the CPD task of the root build;
 * every rule of conventions.md with an automatic check names it. A new rule adds a test here that uses
 * {@link #mainSources()}.
 */
class SourceRulesTest {

    static final List<String> MODULES = List.of("core", "automation", "mcp", "ui");

    /** One Java source of a module's {@code src/main/java}: its path from the repository root and its text. */
    record JavaSource(String module, Path path, String text) {
    }

    /** The repository root: the closest ancestor of the working directory that holds the four modules. */
    static Path repositoryRoot() {
        Path start = Path.of("").toAbsolutePath();
        Path directory = start;
        while (directory != null) {
            if (hasAllModules(directory)) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Repository root not found from " + start);
    }

    private static boolean hasAllModules(Path directory) {
        return MODULES.stream()
                .allMatch(module -> Files.isDirectory(directory.resolve(module).resolve("src/main/java")));
    }

    /** Every Java file of the four modules' {@code src/main/java}, in a stable order. */
    static List<JavaSource> mainSources() {
        Path root = repositoryRoot();
        List<JavaSource> sources = new ArrayList<>();
        for (String module : MODULES) {
            try (Stream<Path> paths = Files.walk(root.resolve(module).resolve("src/main/java"))) {
                for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).sorted().toList()) {
                    sources.add(new JavaSource(module, root.relativize(path), Files.readString(path)));
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return sources;
    }

    @Test
    void findsTheFourModulesSourceTrees() {
        List<JavaSource> sources = mainSources();

        for (String module : MODULES) {
            assertThat(sources).as("sources of " + module).anyMatch(source -> source.module().equals(module));
        }
        // Hundreds of files; a walk that silently found nothing would make every later rule pass.
        assertThat(sources.size()).isGreaterThan(300);
        assertThat(sources).allMatch(source -> source.text().contains("package "));
    }

    @Test
    void noJavadocDanglesOrPointsToItself() {
        List<String> problems = new ArrayList<>();
        for (JavaSource source : mainSources()) {
            for (String problem : JavadocRules.problems(source.text())) {
                problems.add(source.path() + " " + problem);
            }
        }

        assertThat(problems).as("conventions.md §5: a /** */ documents a declaration and never links to itself")
                .isEmpty();
    }
}
