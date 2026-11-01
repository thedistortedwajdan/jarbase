package io.github.thedistortedwajdan.jarbase.core.install;

import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import io.github.thedistortedwajdan.jarbase.core.git.GitException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Installs jarbase's check as a git {@code pre-push} hook, so it also runs when a person pushes by hand
 * from a terminal or an IDE. The file lives in {@code .git/hooks/}, which git never commits, so every clone
 * gets its own copy (the Maven plugin installs it on the first build of each clone).
 */
public final class GitHook {

    /** First lines of the hook; also how we recognise a hook that is ours. */
    static final String MARKER = "# jarbase:git-hook";

    /** What to add to a hook that jarbase must not overwrite. */
    public static final String MANUAL_LINE = "sh .jarbase/jarbase hook git-pre-push \"$@\"";

    private static final String SCRIPT = """
            #!/bin/sh
            # jarbase:git-hook
            # Installed by jarbase. Delete this file to remove it, or skip it once with: git push --no-verify
            root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
            [ -f "$root/.jarbase/jarbase" ] || exit 0
            sh "$root/.jarbase/jarbase" hook git-pre-push "$@"
            status=$?
            # 127 means the jarbase jar is not on this machine yet. That must never stop a push.
            [ "$status" -eq 127 ] && exit 0
            exit "$status"
            """;

    private GitHook() {
    }

    public static Change install(Path root) {
        String shown = ".git/hooks/pre-push";
        try {
            if (GitChanges.config(root, "core.hooksPath").isPresent()) {
                return new Change(Change.Kind.SKIPPED, shown,
                        "core.hooksPath is set (a hook manager such as Husky?); add this line to your pre-push hook: "
                                + MANUAL_LINE);
            }
            Path file = GitChanges.gitPath(root, "hooks/pre-push");
            Optional<String> existing = ProjectFiles.readAbsolute(file);
            if (existing.isPresent() && !existing.get().contains(MARKER)) {
                return new Change(Change.Kind.SKIPPED, shown,
                        "you already have a pre-push hook; add this line to it: " + MANUAL_LINE);
            }
            Change change = ProjectFiles.writeAbsolute(file, SCRIPT, shown);
            file.toFile().setExecutable(true);
            return change;
        } catch (GitException e) {
            return new Change(Change.Kind.SKIPPED, shown, "not a git repository");
        }
    }
}
