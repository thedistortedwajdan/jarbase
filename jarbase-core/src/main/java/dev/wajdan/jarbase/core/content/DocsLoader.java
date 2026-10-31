package dev.wajdan.jarbase.core.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Reads every Markdown page under a docs folder. */
public final class DocsLoader {

    private DocsLoader() {
    }

    /**
     * @param repoRoot   the project's root folder (where {@code .git} lives)
     * @param docsFolder the docs folder, relative to {@code repoRoot} (usually {@code docs})
     * @return all {@code .md} pages, sorted by path; each page's path is relative to {@code repoRoot}
     *         with forward slashes, the same form git uses. Empty if the folder does not exist.
     */
    public static List<Page> load(Path repoRoot, String docsFolder) {
        Path root = repoRoot.toAbsolutePath().normalize();
        return markdownFiles(root, docsFolder).stream().map(path -> readPage(root, path)).toList();
    }

    /**
     * @return the repo-relative path of every {@code .md} file under the docs folder, sorted, with forward
     *         slashes. Empty if the folder does not exist.
     */
    public static List<String> markdownFiles(Path repoRoot, String docsFolder) {
        Path root = repoRoot.toAbsolutePath().normalize();
        Path docs = root.resolve(docsFolder).normalize();
        if (!Files.isDirectory(docs)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(docs)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".md"))
                    .map(file -> toRepoPath(root, file))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the docs folder " + docs, e);
        }
    }

    /** Reads one page by its repo-relative path. */
    public static String readText(Path repoRoot, String repoPath) {
        try {
            return Files.readString(repoRoot.toAbsolutePath().normalize().resolve(repoPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + repoPath, e);
        }
    }

    private static String toRepoPath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static Page readPage(Path root, String repoPath) {
        try {
            String text = Files.readString(root.resolve(repoPath), StandardCharsets.UTF_8);
            return PageParser.parse(repoPath, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + repoPath, e);
        }
    }
}
