package dev.wajdan.jarbase.core.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Asks the real {@code git} program which files changed. We call the user's own git instead of
 * re-implementing it, so the answer always matches what they see on the command line.
 */
public final class GitChanges {

    private static final long TIMEOUT_SECONDS = 30;

    private GitChanges() {
    }

    /** The top folder of the repository that contains {@code anyFolder}. */
    public static Path repoRoot(Path anyFolder) {
        return Path.of(run(anyFolder, "rev-parse", "--show-toplevel").trim());
    }

    /**
     * Files that differ from {@code base}, measured from the point where the current branch split off
     * from it (so later commits on {@code base} are not counted as your changes).
     *
     * @param repoRoot           any folder inside the repository
     * @param base               a branch, tag or commit, for example {@code main} or {@code origin/main}
     * @param includeUncommitted also include edits not committed yet: staged, unstaged and new untracked files
     * @return repo-relative paths with forward slashes, sorted, no duplicates. Renames list both the old and the new path.
     */
    public static List<String> changedFiles(Path repoRoot, String base, boolean includeUncommitted) {
        String mergeBase = run(repoRoot, "merge-base", base, "HEAD").trim();
        if (mergeBase.isEmpty()) {
            throw new GitException("git could not find a common ancestor between '" + base + "' and HEAD");
        }

        TreeSet<String> files = new TreeSet<>();
        if (includeUncommitted) {
            // working tree vs. the split point: committed + staged + unstaged changes to tracked files
            files.addAll(splitNul(run(repoRoot, "diff", "--name-only", "--no-renames", "-z", mergeBase)));
            files.addAll(splitNul(run(repoRoot, "ls-files", "--others", "--exclude-standard", "-z")));
        } else {
            files.addAll(splitNul(run(repoRoot, "diff", "--name-only", "--no-renames", "-z", mergeBase, "HEAD")));
        }
        return List.copyOf(files);
    }

    private static List<String> splitNul(String output) {
        List<String> result = new ArrayList<>();
        for (String entry : output.split("\0")) {
            if (!entry.isEmpty()) {
                result.add(entry);
            }
        }
        return result;
    }

    private static String run(Path dir, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        Process process;
        try {
            process = new ProcessBuilder(command).directory(dir.toFile()).start();
        } catch (IOException e) {
            throw new GitException("Could not run git. Is it installed and on your PATH?", e);
        }

        // Read stderr on another thread so a full pipe can never block git.
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        String stdout = readAll(process.getInputStream());

        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new GitException("git " + String.join(" ", args) + " timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Interrupted while waiting for git", e);
        }

        if (process.exitValue() != 0) {
            throw new GitException("git " + String.join(" ", args) + " failed: " + stderr.join().trim());
        }
        return stdout;
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GitException("Could not read git output", e);
        }
    }
}
