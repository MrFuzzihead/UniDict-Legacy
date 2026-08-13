package com.mrfuzzihead.unidict.common;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * M2 determinism guard — the enforceable form of the PLAN M2 gate ("grep confirms no thread pool in
 * src/main integration code"). It greps the <em>main</em> sources and fails the build if any of the
 * non-deterministic / reflective constructs that caused upstream's crashes are introduced. This
 * turns "don't reintroduce the thread pool or reflection" from a code-review hope into a red test.
 *
 * <p>
 * Scanned: {@code ExecutorService}/{@code Executors} pool creation, {@code invokeAll},
 * reflection instantiation ({@code .newInstance()}, {@code Class::newInstance}), {@code setAccessible}
 * /{@code getDeclaredField}, and any reference to the deleted DI/collection types
 * ({@code Dependencies}, {@code Instantiator}, {@code FixedSizeList}). Mixins use {@code @Accessor} /
 * {@code @Invoker} annotations and the Ore Dictionary bridge, so no legitimate {@code setAccessible}
 * or {@code .newInstance()} exists in {@code src/main} today.
 *
 * <p>
 * A Maven-free pure-scan: no {@code net.minecraft*} or {@code net.minecraftforge*} imports.
 */
class DeterminismGuardTest {

    private static final String MAIN_JAVA = "src/main/java";

    private static final Pattern[] FORBIDDEN = { Pattern.compile("ExecutorService"),
        Pattern.compile("Executors\\.(newFixed|newSingle|newCached)ThreadPool"), Pattern.compile("invokeAll"),
        Pattern.compile("newFixedThreadPool"), Pattern.compile("newSingleThreadExecutor"),
        Pattern.compile("newCachedThreadPool"), Pattern.compile("\\.newInstance\\(\\)"),
        Pattern.compile("Class::newInstance"), Pattern.compile("setAccessible"), Pattern.compile("getDeclaredField"),
        Pattern.compile("\\bDependencies\\b"), Pattern.compile("\\bInstantiator\\b"),
        Pattern.compile("\\bFixedSizeList\\b"), };

    @Test
    void mainSourcesDoNotUseThreadPoolsReflectionOrDeletedDi() {
        final Path root = mainJavaRoot();
        if (!Files.isDirectory(root)) fail(
            "Could not locate main sources at " + root.toAbsolutePath() + " (was the test run from the project root?)");

        final List<String> violations = new ArrayList<>();
        try (final Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                .filter(
                    p -> p.toString()
                        .endsWith(".java"))
                .forEach(p -> violations.addAll(scan(p)));
        } catch (final IOException e) {
            fail("Failed to walk main sources: " + e.getMessage());
        }

        if (!violations.isEmpty()) {
            final StringBuilder sb = new StringBuilder(
                "Forbidden constructs (thread pool / reflection / deleted DI type) in src/main:\n");
            for (final String v : violations) sb.append("  ")
                .append(v)
                .append('\n');
            fail(sb.toString());
        }
    }

    private static Path mainJavaRoot() {
        final Path cwd = Paths.get(MAIN_JAVA);
        return Files.isDirectory(cwd) ? cwd : Paths.get(System.getProperty("user.dir"), MAIN_JAVA);
    }

    private static List<String> scan(final Path file) {
        final List<String> found = new ArrayList<>();
        try {
            final java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i);
                // Ignore comment-only lines so explanatory Javadoc/`//` notes don't false-positive.
                final String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
                for (final Pattern pattern : FORBIDDEN) if (pattern.matcher(line)
                    .find())
                    found.add(
                        file.toString()
                            .replace('\\', '/') + ":"
                            + (i + 1)
                            + " "
                            + pattern.pattern()
                            + " -> "
                            + line.trim());
            }
        } catch (final IOException e) {
            found.add(file + ": could not read: " + e.getMessage());
        }
        return found;
    }
}
