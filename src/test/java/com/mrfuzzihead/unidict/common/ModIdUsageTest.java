package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Source-level guard: every {@code Loader.isModLoaded(...)} call must use a {@link ModIds} constant.
 *
 * <p>
 * This is deliberately a scanner (like {@code DeterminismGuardTest}) rather than a runtime test: the
 * original IE bug was a literal string in the gate, and a runtime unit test cannot load Forge's
 * {@code Loader} without booting the game. Keeping the literals out of the call sites makes the exact
 * id testable in {@link ModIdsTest}.
 */
class ModIdUsageTest {

    private static final String MAIN_JAVA = "src/main/java";

    @Test
    void everyLoaderCallUsesACentralModId() {
        final Path root = mainJavaRoot();
        if (!Files.isDirectory(root)) fail("Could not locate main sources at " + root.toAbsolutePath());

        final List<String> violations = new ArrayList<>();
        try (final Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                .filter(
                    p -> p.toString()
                        .endsWith(".java"))
                .forEach(p -> scan(p, violations));
        } catch (final IOException e) {
            fail("Failed to walk main sources: " + e.getMessage());
        }

        assertTrue(
            violations.isEmpty(),
            "Loader.isModLoaded(...) literals must use ModIds constants:\n" + String.join("\n", violations));
    }

    private static Path mainJavaRoot() {
        final Path cwd = Paths.get(MAIN_JAVA);
        return Files.isDirectory(cwd) ? cwd : Paths.get(System.getProperty("user.dir"), MAIN_JAVA);
    }

    private static void scan(final Path file, final List<String> violations) {
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i);
                final String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
                if (line.contains("Loader.isModLoaded(\"")) {
                    violations.add(
                        file.toString()
                            .replace('\\', '/') + ":"
                            + (i + 1)
                            + " -> "
                            + trimmed);
                }
            }
        } catch (final IOException e) {
            violations.add(file + ": could not read: " + e.getMessage());
        }
    }
}
