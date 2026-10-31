package io.github.thedistortedwajdan.jarbase.core.hook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Remembers which situations the hook has already blocked once, so the same push is never blocked
 * twice. That guarantees an agent (or a person) who decides no docs change is needed is never stuck.
 */
public final class HookState {

    private static final int MAX_ENTRIES = 50;

    private final Path file;

    public HookState(Path file) {
        this.file = file;
    }

    public boolean alreadyBlocked(String key) {
        return read().contains(key);
    }

    public void markBlocked(String key) {
        List<String> keys = read();
        keys.add(key);
        List<String> kept = keys.subList(Math.max(0, keys.size() - MAX_ENTRIES), keys.size());
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, kept);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save hook state to " + file, e);
        }
    }

    private List<String> read() {
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(Files.readAllLines(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read hook state from " + file, e);
        }
    }
}
