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
        Path file = root.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + relativePath, e);
        }
    }

    /** Writes the file (creating folders) unless it already has exactly this content. */
    public static Change write(Path root, String relativePath, String content) {
        Optional<String> existing = read(root, relativePath);
        if (existing.isPresent() && existing.get().equals(content)) {
            return Change.of(Change.Kind.UNCHANGED, relativePath);
        }
        Path file = root.resolve(relativePath);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + relativePath, e);
        }
        return Change.of(existing.isPresent() ? Change.Kind.UPDATED : Change.Kind.CREATED, relativePath);
    }
}
