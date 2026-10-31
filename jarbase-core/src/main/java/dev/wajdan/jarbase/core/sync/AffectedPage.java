package dev.wajdan.jarbase.core.sync;

import dev.wajdan.jarbase.core.content.Page;
import java.util.List;

/**
 * A docs page that describes code which changed.
 *
 * @param page           the page
 * @param matchedSources the page's {@code sources:} entries that matched at least one changed file
 * @param changedFiles   the changed files that caused the match, sorted
 */
public record AffectedPage(Page page, List<String> matchedSources, List<String> changedFiles) {
}
