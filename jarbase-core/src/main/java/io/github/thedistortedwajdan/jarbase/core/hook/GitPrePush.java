package io.github.thedistortedwajdan.jarbase.core.hook;

import io.github.thedistortedwajdan.jarbase.core.Jarbase;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;
import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedPage;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedReport;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rule behind the git {@code pre-push} hook, which runs for every push no matter who starts it.
 * Git tells the hook what is about to be pushed, one line per branch:
 * {@code <local ref> <local sha> <remote ref> <remote sha>}.
 */
public final class GitPrePush {

    private static final String ZEROS = "0000000000000000000000000000000000000000";
    private static final String STATE_FILE = "jarbase-git-hook-state";

    private GitPrePush() {
    }

    /** One branch that git is about to push. */
    public record RefUpdate(String localRef, String localSha, String remoteRef, String remoteSha) {

        /** True for a push that creates or updates a branch (not a deletion, a tag or another kind of ref). */
        public boolean isBranchPush() {
            return localRef.startsWith("refs/heads/") && !localSha.matches("0+");
        }

        public String branch() {
            return localRef.substring("refs/heads/".length());
        }
    }

    /**
     * What the hook decided.
     *
     * @param block   true to stop the push
     * @param message what to tell the person; may be empty
     */
    public record Result(boolean block, String message) {

        static Result allow() {
            return new Result(false, "");
        }
    }

    /** Reads the lines git writes to the hook's standard input. */
    public static List<RefUpdate> parse(String stdin) {
        List<RefUpdate> updates = new ArrayList<>();
        for (String line : stdin.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 4) {
                updates.add(new RefUpdate(parts[0], parts[1], parts[2], parts[3]));
            }
        }
        return updates;
    }

    /**
     * @param anyFolder   any folder inside the repository being pushed
     * @param docsFolder  the docs folder relative to the repository root
     * @param updates     what git is about to push
     * @param config      jarbase's settings
     * @param insideAgent true when a coding agent started this push; the agent has its own hook, so this one only warns
     */
    public static Result run(Path anyFolder, String docsFolder, List<RefUpdate> updates, JarbaseConfig config,
            boolean insideAgent) {
        Path root = GitChanges.repoRoot(anyFolder);

        Map<String, AffectedPage> stale = new LinkedHashMap<>();
        List<String> pushedShas = new ArrayList<>();
        String base = null;
        for (RefUpdate update : updates) {
            if (!update.isBranchPush()) {
                continue;
            }
            base = GitChanges.pushBase(root, update.branch());
            AffectedReport report = AffectedService.run(root, docsFolder, base, update.localSha(), false);
            report.pages().stream().filter(p -> !p.updated()).forEach(p -> stale.putIfAbsent(p.page().path(), p));
            pushedShas.add(update.localSha());
        }
        if (stale.isEmpty()) {
            return Result.allow();
        }

        List<AffectedPage> pages = List.copyOf(stale.values());
        JarbaseConfig.OnPush mode = insideAgent ? JarbaseConfig.OnPush.WARN : config.onPush();

        if (mode == JarbaseConfig.OnPush.BLOCK) {
            String key = pushedShas + "|" + pages.stream().map(p -> p.page().path()).toList();
            HookState state = new HookState(GitChanges.gitPath(root, STATE_FILE));
            if (!state.alreadyBlocked(key)) {
                state.markBlocked(key);
                return new Result(true, blockMessage(pages, base));
            }
        }
        return new Result(false, warnMessage(pages, base));
    }

    static String warnMessage(List<AffectedPage> stale, String base) {
        StringBuilder out = new StringBuilder();
        out.append("jarbase: pushing anyway, but ").append(describe(stale))
                .append(" code in this push and ").append(stale.size() == 1 ? "was" : "were").append(" not updated:\n");
        appendPages(out, stale);
        out.append("To fix it later: update the page(s), then run `").append(Jarbase.CLI_COMMAND)
                .append(" affected --base ").append(base).append("` to check.");
        return out.toString();
    }

    static String blockMessage(List<AffectedPage> stale, String base) {
        StringBuilder out = new StringBuilder();
        out.append("jarbase: push stopped once, because ").append(describe(stale))
                .append(" code in this push and ").append(stale.size() == 1 ? "was" : "were").append(" not updated:\n");
        appendPages(out, stale);
        out.append("Update the page(s) and commit them, then push again. To push without updating, just push again")
                .append(" (or use `git push --no-verify`).\n");
        out.append("Check with: ").append(Jarbase.CLI_COMMAND).append(" affected --base ").append(base);
        return out.toString();
    }

    private static String describe(List<AffectedPage> stale) {
        return stale.size() + (stale.size() == 1 ? " docs page describes" : " docs pages describe");
    }

    private static void appendPages(StringBuilder out, List<AffectedPage> stale) {
        for (AffectedPage affected : stale) {
            out.append("  - ").append(affected.page().path())
                    .append("   (sources: ").append(String.join(", ", affected.matchedSources())).append(")\n");
        }
    }
}
