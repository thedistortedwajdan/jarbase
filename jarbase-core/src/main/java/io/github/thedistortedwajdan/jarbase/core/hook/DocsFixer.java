package io.github.thedistortedwajdan.jarbase.core.hook;

import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.agent.AgentRunner;
import io.github.thedistortedwajdan.jarbase.core.agent.HeadlessCommand;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;
import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import io.github.thedistortedwajdan.jarbase.core.git.GitException;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedPage;
import io.github.thedistortedwajdan.jarbase.core.validation.DocsValidator;
import io.github.thedistortedwajdan.jarbase.core.validation.Issue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Asks the developer's own coding agent to bring stale docs pages up to date, then commits and pushes them.
 *
 * <p>Everything here is defensive, because it runs inside {@code git push}: the agent may only change files under
 * the docs folder (anything else is undone), it may not make the docs worse (new validation errors are undone),
 * it has a time limit, and any failure leaves the working tree exactly as it was.
 */
public final class DocsFixer {

    private DocsFixer() {
    }

    /** How it ended. */
    public enum Status {
        /** Docs updated, committed, and the branch pushed. The push that started this hook is now redundant. */
        PUSHED,
        /** Docs updated but left uncommitted for the person to review (review mode {@code required}). */
        READY_FOR_REVIEW,
        /** Nothing was changed; the reason is in the message. */
        FAILED
    }

    /**
     * @param status  how it ended
     * @param message what to tell the person
     */
    public record Outcome(Status status, String message) {

        static Outcome failed(String reason) {
            return new Outcome(Status.FAILED, reason);
        }
    }

    /**
     * Everything needed to try a fix.
     *
     * @param root     the repository's top folder
     * @param docs     the docs folder relative to {@code root}
     * @param update   the branch being pushed
     * @param remote   the remote name or URL git is pushing to
     * @param base     what the branch is compared against
     * @param stale    the pages that need updating
     * @param config   jarbase's settings
     * @param env      the process environment (used to find the agent on the {@code PATH})
     * @param adapters the coding agents jarbase knows about
     * @param progress where to report what is happening
     */
    public record Request(Path root, String docs, GitPrePush.RefUpdate update, String remote, String base,
            List<AffectedPage> stale, JarbaseConfig config, Map<String, String> env, List<AgentAdapter> adapters,
            Consumer<String> progress) {
    }

    private record Chosen(AgentAdapter adapter, Path executable, HeadlessCommand command) {
    }

    public static Outcome fix(Request request) {
        try {
            return attempt(request);
        } catch (GitException | UncheckedIOException e) {
            return Outcome.failed(e.getMessage());
        }
    }

    private static Outcome attempt(Request r) {
        Path root = r.root();
        String docs = r.docs();

        // Only the branch that is checked out can be fixed: the edits happen in the working tree.
        if (!r.update().localSha().equals(GitChanges.headCommit(root))
                || !r.update().branch().equals(GitChanges.currentBranch(root))) {
            return Outcome.failed("only the checked-out branch can be updated automatically");
        }
        if (dirty(root).keySet().stream().anyMatch(p -> underDocs(p, docs))) {
            return Outcome.failed("there are uncommitted changes under " + docs + "/; commit or stash them first");
        }

        String instruction = FixPrompt.build(docs, r.base(), r.stale());
        Optional<Chosen> chosen = choose(r, instruction);
        if (chosen.isEmpty()) {
            return Outcome.failed(noAgentMessage(r));
        }
        Chosen agent = chosen.get();

        Map<String, String> before = dirty(root);
        Set<String> errorsBefore = errors(root, docs);

        r.progress().accept("jarbase: " + r.stale().size() + (r.stale().size() == 1 ? " docs page needs" : " docs pages need")
                + " updating. Asking " + agent.adapter().id() + " to update "
                + (r.stale().size() == 1 ? "it" : "them") + " (up to " + r.config().timeoutSeconds() + "s)...");
        AgentRunner.Result run = AgentRunner.run(agent.executable(), agent.command().args(), agent.command().stdin(), root,
                Map.of("JARBASE_HOOKS", "off"), Duration.ofSeconds(r.config().timeoutSeconds()));

        Map<String, String> after = dirty(root);
        Map<String, String> changed = new LinkedHashMap<>();
        after.forEach((path, code) -> {
            if (!before.containsKey(path) || !before.get(path).equals(code)) {
                changed.put(path, code);
            }
        });

        if (!run.started()) {
            return Outcome.failed("could not start " + agent.adapter().id() + " (" + run.output() + ")");
        }
        if (run.timedOut()) {
            revert(root, changed);
            return Outcome.failed(agent.adapter().id() + " took longer than " + r.config().timeoutSeconds() + "s");
        }
        if (run.exitCode() != 0) {
            revert(root, changed);
            return Outcome.failed(agent.adapter().id() + " stopped with an error"
                    + (run.output().isEmpty() ? "" : ": " + oneLine(run.output())));
        }

        List<String> outside = changed.keySet().stream().filter(p -> !underDocs(p, docs)).toList();
        if (!outside.isEmpty()) {
            revert(root, changed);
            return Outcome.failed(agent.adapter().id() + " changed files outside " + docs + "/ (" + String.join(", ", outside)
                    + "); all of its changes were undone");
        }
        if (changed.isEmpty()) {
            return Outcome.failed(agent.adapter().id() + " made no changes");
        }

        Set<String> newErrors = errors(root, docs);
        newErrors.removeAll(errorsBefore);
        if (!newErrors.isEmpty()) {
            revert(root, changed);
            return Outcome.failed("the agent's edits introduced docs problems, so they were undone: "
                    + String.join("; ", newErrors));
        }

        List<AffectedPage> fixed = r.stale().stream().filter(p -> changed.containsKey(p.page().path())).toList();
        if (fixed.isEmpty()) {
            revert(root, changed);
            return Outcome.failed(agent.adapter().id() + " did not update the pages that describe the pushed code");
        }
        List<AffectedPage> stillStale = r.stale().stream().filter(p -> !fixed.contains(p)).toList();
        String pagesText = fixed.stream().map(p -> p.page().path()).collect(Collectors.joining(", "));
        String leftover = stillStale.isEmpty() ? ""
                : " Still not updated: " + stillStale.stream().map(p -> p.page().path()).collect(Collectors.joining(", ")) + ".";

        if (r.config().review() == JarbaseConfig.Review.REQUIRED) {
            return new Outcome(Status.READY_FOR_REVIEW, "jarbase: " + agent.adapter().id() + " updated " + pagesText
                    + ". Nothing was committed or pushed yet." + leftover + "\n"
                    + "Review the change with `git diff -- " + docs + "`, then commit it and push again.");
        }
        return commitAndPush(r, agent, changed.keySet().stream().toList(), pagesText, leftover);
    }

    private static Outcome commitAndPush(Request r, Chosen agent, List<String> paths, String pagesText, String leftover) {
        Path root = r.root();
        String branch = r.update().branch();
        try {
            List<String> add = new ArrayList<>(List.of("add", "--"));
            add.addAll(paths);
            GitChanges.git(root, add.toArray(String[]::new));

            List<String> commit = new ArrayList<>(List.of("commit", "-q", "-m",
                    "docs: update for code changes on " + branch + " (jarbase)", "--"));
            commit.addAll(paths);
            GitChanges.git(root, commit.toArray(String[]::new));
        } catch (GitException e) {
            return Outcome.failed("the docs were updated but could not be committed (" + oneLine(e.getMessage())
                    + "); the edits are in your working tree");
        }

        try {
            GitChanges.git(root, 120, "push", "--no-verify", r.remote(),
                    r.update().localRef() + ":" + r.update().remoteRef());
        } catch (GitException e) {
            return Outcome.failed("the docs were updated and committed, but pushing them failed ("
                    + oneLine(e.getMessage()) + "). Push again when you can");
        }
        return new Outcome(Status.PUSHED, "jarbase: " + agent.adapter().id() + " updated " + pagesText + "; the docs were committed and "
                + branch + " was pushed with them." + leftover + "\n"
                + "The \"failed to push\" message git prints next is expected: your push is already done.");
    }

    // ---- choosing the agent ----

    private static Optional<Chosen> choose(Request r, String instruction) {
        for (AgentAdapter adapter : r.adapters()) {
            if (r.config().agent() != null && !adapter.id().equals(r.config().agent())) {
                continue;
            }
            Optional<HeadlessCommand> command = adapter.headlessCommand(instruction, r.docs());
            if (command.isEmpty()) {
                continue;
            }
            Optional<Path> executable = AgentRunner.findExecutable(command.get().program(), r.env());
            if (executable.isPresent()) {
                return Optional.of(new Chosen(adapter, executable.get(), command.get()));
            }
        }
        return Optional.empty();
    }

    private static String noAgentMessage(Request r) {
        if (r.config().agent() != null) {
            boolean known = r.adapters().stream().anyMatch(a -> a.id().equals(r.config().agent()));
            return known
                    ? "the coding agent '" + r.config().agent() + "' was not found on this machine"
                    : "unknown agent '" + r.config().agent() + "' (available: "
                            + r.adapters().stream().map(AgentAdapter::id).collect(Collectors.joining(", ")) + ")";
        }
        String looked = r.adapters().stream()
                .map(a -> a.headlessCommand("", r.docs()).map(HeadlessCommand::program).orElse(null))
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.joining(", "));
        return "no coding agent found on this machine (looked for: " + (looked.isEmpty() ? "none" : looked) + ")";
    }

    // ---- reading and undoing changes in the working tree ----

    /** Every changed or new file, mapped to its two-letter git status code. */
    private static Map<String, String> dirty(Path root) {
        String output = GitChanges.git(root, "status", "--porcelain", "-z", "--no-renames", "--untracked-files=all");
        Map<String, String> files = new LinkedHashMap<>();
        for (String entry : output.split("\0")) {
            if (entry.length() > 3) {
                files.put(entry.substring(3), entry.substring(0, 2));
            }
        }
        return files;
    }

    /** Puts back every file in {@code changed}: tracked files are restored, new files are deleted. */
    private static void revert(Path root, Map<String, String> changed) {
        for (Map.Entry<String, String> entry : changed.entrySet()) {
            Path file = root.resolve(entry.getKey());
            try {
                if (entry.getValue().startsWith("?")) {
                    Files.deleteIfExists(file);
                } else {
                    GitChanges.git(root, "checkout", "HEAD", "--", entry.getKey());
                }
            } catch (IOException | GitException ignored) {
                // best effort: the person is told the edits are in their working tree if this fails
            }
        }
    }

    private static Set<String> errors(Path root, String docs) {
        return DocsValidator.validate(root, docs).issues().stream()
                .filter(i -> i.severity() == Issue.Severity.ERROR)
                .map(i -> i.page() + ": " + i.message())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static boolean underDocs(String path, String docs) {
        return path.startsWith(docs + "/");
    }

    private static String oneLine(String text) {
        String line = text.strip().replaceAll("\\s+", " ");
        return line.length() <= 300 ? line : line.substring(0, 300) + "...";
    }
}
