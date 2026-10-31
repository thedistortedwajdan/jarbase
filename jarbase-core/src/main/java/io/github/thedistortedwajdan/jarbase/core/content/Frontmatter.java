package io.github.thedistortedwajdan.jarbase.core.content;

import java.util.List;

/**
 * The settings written in the YAML block at the top of a docs page.
 *
 * @param title       page title; may be null when the page has neither a {@code title} key nor an H1
 * @param description one-line summary, or null
 * @param order       sort position in navigation, or null
 * @param sources     globs of the code this page describes, for example {@code src/main/java/com/acme/billing/**}
 * @param tags        free-form labels
 * @param hidden      true when the page is reachable but not listed in navigation
 */
public record Frontmatter(
        String title,
        String description,
        Integer order,
        List<String> sources,
        List<String> tags,
        boolean hidden) {

    public Frontmatter {
        sources = List.copyOf(sources);
        tags = List.copyOf(tags);
    }

    public static Frontmatter empty() {
        return new Frontmatter(null, null, null, List.of(), List.of(), false);
    }
}
