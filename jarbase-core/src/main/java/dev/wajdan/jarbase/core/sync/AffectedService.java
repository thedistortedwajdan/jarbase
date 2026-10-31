package dev.wajdan.jarbase.core.sync;

import dev.wajdan.jarbase.core.content.DocsLoader;
import dev.wajdan.jarbase.core.content.Page;
import dev.wajdan.jarbase.core.git.GitChanges;
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
        Path root = GitChanges.repoRoot(anyFolder);
        List<String> changed = GitChanges.changedFiles(root, base, includeUncommitted);
        List<Page> pages = DocsLoader.load(root, docsFolder);
        return new AffectedReport(base, AffectedFinder.find(pages, changed));
    }
}
