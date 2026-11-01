package io.github.thedistortedwajdan.jarbase.core.sync;

import io.github.thedistortedwajdan.jarbase.core.content.DocsLoader;
import io.github.thedistortedwajdan.jarbase.core.content.Page;
import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import java.nio.file.Path;
import java.util.List;

/** Wires the pieces together: git says what changed, the loader reads the docs, the finder matches them. */
public final class AffectedService {

    private AffectedService() {
    }

    /**
     * @param anyFolder          any folder inside the git repository (usually the current directory)
     * @param docsFolder         the docs folder relative to the repository root, usually {@code docs}
     * @param base               branch, tag or commit to compare against
     * @param includeUncommitted also count edits that are not committed yet
     */
    public static AffectedReport run(Path anyFolder, String docsFolder, String base, boolean includeUncommitted) {
        return run(anyFolder, docsFolder, base, "HEAD", includeUncommitted);
    }

    /** Like {@link #run(Path, String, String, boolean)}, for any commit instead of the checked-out one. */
    public static AffectedReport run(Path anyFolder, String docsFolder, String base, String head,
            boolean includeUncommitted) {
        Path root = GitChanges.repoRoot(anyFolder);
        List<String> changed = GitChanges.changedFiles(root, base, head, includeUncommitted);
        List<Page> pages = DocsLoader.load(root, docsFolder);
        return new AffectedReport(base, AffectedFinder.find(pages, changed));
    }
}
