package io.github.thedistortedwajdan.jarbase.core.hook;

import io.github.thedistortedwajdan.jarbase.core.Jarbase;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedPage;
import java.util.List;

/** The instruction given to a coding agent when it is asked to bring stale docs pages up to date. */
public final class FixPrompt {

    private FixPrompt() {
    }

    /**
     * @param docsFolder the only folder the agent may edit
     * @param base       what the branch is compared against, used in the {@code git diff} the agent is told to run
     * @param stale      the pages that describe changed code but were not updated
     */
    public static String build(String docsFolder, String base, List<AffectedPage> stale) {
        StringBuilder pages = new StringBuilder();
        for (AffectedPage affected : stale) {
            pages.append("- ").append(affected.page().path())
                    .append("  (describes: ").append(String.join(", ", affected.matchedSources()))
                    .append("; changed files: ").append(String.join(", ", affected.changedFiles())).append(")\n");
        }
        return """
                You are updating documentation so that it matches code that changed on this git branch.

                These docs pages describe code that changed, and were not updated:
                %s
                What to do:
                1. Run `git diff %s...HEAD -- <the changed files>` to see exactly what changed in the code.
                2. Read each page above. Update only what the change makes wrong or incomplete, in the page's own
                   style. Leave unrelated text untouched.
                3. Edit only files under %s/. Do not change code. Do not run git commit or git push.
                4. Run `%s validate` and fix any problem it reports in the pages you edited.
                5. Never write secrets or configuration values into the docs.

                When you finish, reply with one short line per page saying what you changed.
                """.formatted(pages, base, docsFolder, Jarbase.CLI_COMMAND);
    }
}
