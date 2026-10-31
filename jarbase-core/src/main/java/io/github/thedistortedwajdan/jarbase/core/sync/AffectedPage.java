package io.github.thedistortedwajdan.jarbase.core.sync;

import io.github.thedistortedwajdan.jarbase.core.content.Page;
import java.util.List;

/**
 * A docs page that describes code which changed.
 *
 * @param page           the page
 * @param matchedSources the page's {@code sources:} entries that matched at least one changed file
 * @param changedFiles   the changed files that caused the match, sorted
 * @param updated        true when the page file itself is among the changed files, meaning the docs were touched too
 */
public record AffectedPage(Page page, List<String> matchedSources, List<String> changedFiles, boolean updated) {
}
