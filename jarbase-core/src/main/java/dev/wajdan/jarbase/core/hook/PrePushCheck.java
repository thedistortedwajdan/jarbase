package dev.wajdan.jarbase.core.hook;

import dev.wajdan.jarbase.core.git.GitChanges;
import dev.wajdan.jarbase.core.sync.AffectedPage;
import dev.wajdan.jarbase.core.sync.AffectedReport;
import dev.wajdan.jarbase.core.sync.AffectedService;
import java.nio.file.Path;
import java.util.List;

/**
 * The rule behind the push hook: block a push once when code changed but the docs pages that
 * describe it did not. Deterministic and offline; it reads git and the docs and never calls an AI.
 */
public final class PrePushCheck {

    private static final String STATE_FILE = "jarbase-hook-state";

    private PrePushCheck() {
    }

    /**
     * @param anyFolder  any folder inside the repository about to be pushed
     * @param docsFolder docs folder relative to the repository root
     * @param base       what to compare against, or null to work it out (default branch or upstream)
     */
    public static HookDecision decide(Path anyFolder, String docsFolder, String base) {
        Path root = GitChanges.repoRoot(anyFolder);
        String compareWith = base != null ? base : GitChanges.pushBase(root);

        AffectedReport report = AffectedService.run(root, docsFolder, compareWith, true);
        if (!report.hasStalePages()) {
            return HookDecision.allow();
        }

        List<AffectedPage> stale = report.pages().stream().filter(p -> !p.updated()).toList();
        String key = GitChanges.headCommit(root) + "|" + stale.stream().map(p -> p.page().path()).toList();

        HookState state = new HookState(GitChanges.gitPath(root, STATE_FILE));
        if (state.alreadyBlocked(key)) {
            return HookDecision.allow();
        }
        state.markBlocked(key);
        return HookDecision.block(message(stale, compareWith));
    }

    static String message(List<AffectedPage> stale, String base) {
        StringBuilder out = new StringBuilder();
        out.append("jarbase: this push changes code that ").append(stale.size())
                .append(stale.size() == 1 ? " docs page describes" : " docs pages describe")
                .append(", and ").append(stale.size() == 1 ? "it was" : "they were").append(" not updated:\n");
        for (AffectedPage affected : stale) {
            out.append("  - ").append(affected.page().path())
                    .append("   (sources: ").append(String.join(", ", affected.matchedSources())).append(")\n");
        }
        out.append("Update ").append(stale.size() == 1 ? "it" : "them")
                .append(", then run `jarbase affected --uncommitted --base ").append(base).append("` to check.\n");
        out.append("Show the user the docs diff before committing it, then commit the docs with the code and push again.\n");
        out.append("If no docs change is needed, tell the user why and push again; the next push is allowed.");
        return out.toString();
    }
}
