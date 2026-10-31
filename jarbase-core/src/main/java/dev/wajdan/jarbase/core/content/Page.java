package dev.wajdan.jarbase.core.content;

/**
 * One docs page.
 *
 * @param path        where the page lives, relative to the docs folder, with forward slashes (for example {@code billing/webhooks.md})
 * @param frontmatter the parsed settings block
 * @param body        the Markdown after the settings block
 */
public record Page(String path, Frontmatter frontmatter, String body) {
}
