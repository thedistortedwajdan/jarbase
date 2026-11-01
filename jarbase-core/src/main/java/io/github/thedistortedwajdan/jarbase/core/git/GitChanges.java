package io.github.thedistortedwajdan.jarbase.core.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
     * Every file git knows about or would add: tracked files plus new files that are not ignored.
     * Used to check that docs point at code and files that exist.
     */
    public static List<String> repoFiles(Path repoRoot) {
        return splitNul(run(repoRoot, "ls-files", "-z", "--cached", "--others", "--exclude-standard"));
    }

    /** The commit currently checked out, or {@code "none"} in a repository with no commits yet. */
    public static String headCommit(Path repoRoot) {
        return tryRun(repoRoot, "rev-parse", "--verify", "--quiet", "HEAD").orElse("none");
    }

    /** A file inside the repository's private {@code .git} folder, so it is never committed. */
    public static Path gitPath(Path repoRoot, String name) {
        Path path = Path.of(run(repoRoot, "rev-parse", "--git-path", name).trim());
        return path.isAbsolute() ? path : repoRoot.resolve(path);
    }

    /**
     * The branch most pushes are measured against: {@code origin/HEAD} when the repository has a remote,
     * otherwise a local {@code main} or {@code master}.
     */
    public static String defaultBranch(Path repoRoot) {
        var remoteHead = tryRun(repoRoot, "symbolic-ref", "--short", "refs/remotes/origin/HEAD");
        if (remoteHead.isPresent()) {
            return remoteHead.get();
        }
        for (String candidate : List.of("main", "master")) {
            if (tryRun(repoRoot, "rev-parse", "--verify", "--quiet", "refs/heads/" + candidate).isPresent()) {
                return candidate;
            }
        }
        return "main";
    }

    /**
     * What to compare against when the current work is about to be pushed. Normally the default branch.
     * When you are working directly on the default branch, it is the branch's upstream, so only
     * commits that are not pushed yet count.
     */
    public static String pushBase(Path repoRoot) {
        return pushBase(repoRoot, tryRun(repoRoot, "branch", "--show-current").orElse(""));
    }

    /** Like {@link #pushBase(Path)}, for a named local branch that is about to be pushed. */
    public static String pushBase(Path repoRoot, String branch) {
        String defaultBranch = defaultBranch(repoRoot);
        String defaultShortName = defaultBranch.contains("/")
                ? defaultBranch.substring(defaultBranch.indexOf('/') + 1)
                : defaultBranch;
        if (!branch.isEmpty() && branch.equals(defaultShortName)
                && tryRun(repoRoot, "rev-parse", "--verify", "--quiet", branch + "@{upstream}").isPresent()) {
            return branch + "@{upstream}";
        }
        return defaultBranch;
    }

    /** The checked-out branch's name, or empty when HEAD is detached. */
    public static String currentBranch(Path repoRoot) {
        return tryRun(repoRoot, "branch", "--show-current").orElse("");
    }

    /** A git setting such as {@code core.hooksPath}, or empty when it is not set. */
    public static Optional<String> config(Path repoRoot, String key) {
        return tryRun(repoRoot, "config", "--get", key);
    }

    /** Runs any git command and returns its output; throws {@link GitException} if git fails. */
    public static String git(Path dir, String... args) {
        return run(dir, args);
    }

    /** Runs git and returns trimmed output, or nothing if git fails or prints nothing. */
    private static Optional<String> tryRun(Path dir, String... args) {
        try {
            String output = run(dir, args).trim();
            return output.isEmpty() ? Optional.empty() : Optional.of(output);
        } catch (GitException e) {
            return Optional.empty();
        }
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
        return changedFiles(repoRoot, base, "HEAD", includeUncommitted);
    }

    /**
     * Like {@link #changedFiles(Path, String, boolean)}, for any commit or branch instead of the checked-out one.
     * Uncommitted edits only make sense for {@code HEAD}, so they are ignored for anything else.
     *
     * @param head the commit whose changes are wanted, for example {@code HEAD} or a SHA
     */
    public static List<String> changedFiles(Path repoRoot, String base, String head, boolean includeUncommitted) {
        String mergeBase = run(repoRoot, "merge-base", base, head).trim();
        if (mergeBase.isEmpty()) {
            throw new GitException("git could not find a common ancestor between '" + base + "' and " + head);
        }

        TreeSet<String> files = new TreeSet<>();
        if (includeUncommitted && head.equals("HEAD")) {
            // working tree vs. the split point: committed + staged + unstaged changes to tracked files
            files.addAll(splitNul(run(repoRoot, "diff", "--name-only", "--no-renames", "-z", mergeBase)));
            files.addAll(splitNul(run(repoRoot, "ls-files", "--others", "--exclude-standard", "-z")));
        } else {
            files.addAll(splitNul(run(repoRoot, "diff", "--name-only", "--no-renames", "-z", mergeBase, head)));
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
