package dev.wajdan.jarbase.core.sync;

import dev.wajdan.jarbase.core.content.Page;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Answers: which docs pages describe the code that just changed? Pure logic; no git, no files. */
public final class AffectedFinder {

    private AffectedFinder() {
    }

    /**
     * @param pages        every docs page
     * @param changedFiles repo-relative paths of the changed files
     * @return the pages whose {@code sources:} match at least one changed file, ordered by page path
     */
    public static List<AffectedPage> find(Collection<Page> pages, Collection<String> changedFiles) {
        List<String> changed = changedFiles.stream().map(SourceGlob::normalizePath).toList();
        List<AffectedPage> affected = new ArrayList<>();

        for (Page page : pages) {
            TreeSet<String> matchedSources = new TreeSet<>();
            TreeSet<String> matchedFiles = new TreeSet<>();

            for (String spec : page.frontmatter().sources()) {
                SourceGlob glob = compile(page, spec);
                for (String file : changed) {
                    if (glob.matches(file)) {
                        matchedSources.add(spec);
                        matchedFiles.add(file);
                    }
                }
            }

            if (!matchedFiles.isEmpty()) {
                boolean updated = changed.contains(SourceGlob.normalizePath(page.path()));
                affected.add(new AffectedPage(page, List.copyOf(matchedSources), List.copyOf(matchedFiles), updated));
            }
        }

        affected.sort(Comparator.comparing(a -> a.page().path()));
        return affected;
    }

    private static SourceGlob compile(Page page, String spec) {
        try {
            return SourceGlob.of(spec);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Bad sources entry in " + page.path() + ": " + e.getMessage(), e);
        }
    }
}
