package io.github.thedistortedwajdan.jarbase.core.install;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Small file helpers shared by the installer and the agent adapters. */
public final class ProjectFiles {

    private ProjectFiles() {
    }

    public static Optional<String> read(Path root, String relativePath) {
        return readAbsolute(root.resolve(relativePath));
    }

    public static Optional<String> readAbsolute(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    /** Writes the file (creating folders) unless it already has exactly this content. */
    public static Change write(Path root, String relativePath, String content) {
        return writeAbsolute(root.resolve(relativePath), content, relativePath);
    }

    /**
     * Like {@link #write}, for a file that may be outside the project folder.
     *
     * @param shownPath how the file is named in the {@link Change} that is returned
     */
    public static Change writeAbsolute(Path file, String content, String shownPath) {
        Optional<String> existing = readAbsolute(file);
        if (existing.isPresent() && existing.get().equals(content)) {
            return Change.of(Change.Kind.UNCHANGED, shownPath);
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
        return Change.of(existing.isPresent() ? Change.Kind.UPDATED : Change.Kind.CREATED, shownPath);
    }

    /** Writes the file only if it does not exist yet, so a person's edits are never overwritten. */
    public static Change createIfAbsent(Path root, String relativePath, String content) {
        if (Files.exists(root.resolve(relativePath))) {
            return new Change(Change.Kind.UNCHANGED, relativePath, "kept your settings");
        }
        return write(root, relativePath, content);
    }
}
