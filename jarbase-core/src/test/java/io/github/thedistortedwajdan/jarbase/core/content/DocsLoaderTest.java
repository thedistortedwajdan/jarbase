package io.github.thedistortedwajdan.jarbase.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsLoaderTest {

    @TempDir
    Path repo;

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void loadsMarkdownPagesWithRepoRelativePaths() throws IOException {
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\n");
        write("docs/guides/setup.md", "# Setup\n");

        List<Page> pages = DocsLoader.load(repo, "docs");

        assertThat(pages).extracting(Page::path).containsExactly("docs/billing.md", "docs/guides/setup.md");
        assertThat(pages.get(0).frontmatter().sources()).containsExactly("src/billing/**");
        assertThat(pages.get(1).frontmatter().title()).isEqualTo("Setup");
    }

    @Test
    void ignoresFilesThatAreNotMarkdown() throws IOException {
        write("docs/a.md", "# A\n");
        write("docs/logo.png", "not really a png");
        write("docs/notes.txt", "notes");

        assertThat(DocsLoader.load(repo, "docs")).extracting(Page::path).containsExactly("docs/a.md");
    }

    @Test
    void doesNotReadMarkdownOutsideTheDocsFolder() throws IOException {
        write("docs/a.md", "# A\n");
        write("README.md", "# Readme\n");

        assertThat(DocsLoader.load(repo, "docs")).extracting(Page::path).containsExactly("docs/a.md");
    }

    @Test
    void missingDocsFolderGivesNoPages() {
        assertThat(DocsLoader.load(repo, "docs")).isEmpty();
    }

    @Test
    void brokenFrontmatterIsReportedWithThePagePath() throws IOException {
        write("docs/bad.md", "---\ntitle: [unclosed\n---\n");

        assertThatThrownBy(() -> DocsLoader.load(repo, "docs"))
                .isInstanceOf(InvalidFrontmatterException.class)
                .hasMessageContaining("docs/bad.md");
    }
}
