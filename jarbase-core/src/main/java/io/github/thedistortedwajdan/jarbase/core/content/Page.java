package io.github.thedistortedwajdan.jarbase.core.content;

/**
 * One docs page.
 *
 * @param path        where the page lives, relative to the repository root, with forward slashes (for example {@code docs/billing/webhooks.md})
 * @param frontmatter the parsed settings block
 * @param body        the Markdown after the settings block
 */
public record Page(String path, Frontmatter frontmatter, String body) {
}
