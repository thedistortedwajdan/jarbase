package io.github.thedistortedwajdan.jarbase.core.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsValidatorTest {

    @TempDir
    Path root;

    private final List<String> repoFiles = new ArrayList<>(List.of("src/billing/Invoice.java", "src/users/User.java"));

    private void page(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        repoFiles.add(path);
    }

    private ValidationReport validate() {
        return DocsValidator.validate(root, "docs", repoFiles);
    }

    private static List<String> rules(ValidationReport report) {
        return report.issues().stream().map(Issue::rule).toList();
    }

    @Test
    void healthyDocsHaveNoIssues() throws IOException {
        page("docs/index.md", "---\ntitle: Home\n---\n# Home\nSee [billing](billing.md).\n");
        page("docs/billing.md", "---\ntitle: Billing\nsources: [\"src/billing/**\"]\n---\n# Billing\nText.\n");

        ValidationReport report = validate();

        assertThat(report.issues()).isEmpty();
        assertThat(report.pageCount()).isEqualTo(2);
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.text()).isEqualTo("Docs look good: 2 pages checked, no problems.\n");
    }

    @Test
    void unreadableFrontmatterIsAnError() throws IOException {
        page("docs/bad.md", "---\ntitle: [unclosed\n---\n# Bad\n");

        ValidationReport report = validate();

        assertThat(rules(report)).containsExactly("frontmatter");
        assertThat(report.hasErrors()).isTrue();
        assertThat(report.issues().get(0).page()).isEqualTo("docs/bad.md");
    }

    @Test
    void pageWithoutTitleOrHeadingIsAnError() throws IOException {
        page("docs/a.md", "Just some text, no heading.\n");

        assertThat(rules(validate())).containsExactly("title");
    }

    @Test
    void headingCountsAsTheTitle() throws IOException {
        page("docs/a.md", "# Has a heading\nText.\n");

        assertThat(validate().issues()).isEmpty();
    }

    @Test
    void emptyPageIsAWarning() throws IOException {
        page("docs/a.md", "---\ntitle: Empty\n---\n");

        ValidationReport report = validate();

        assertThat(rules(report)).containsExactly("empty-page");
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.warningCount()).isEqualTo(1);
    }

    @Test
    void sourcesThatMatchNothingAreAWarning() throws IOException {
        page("docs/a.md", "---\ntitle: A\nsources: [\"src/gone/**\", \"src/billing/**\"]\n---\nText\n");

        ValidationReport report = validate();

        assertThat(report.issues()).hasSize(1);
        assertThat(report.issues().get(0).severity()).isEqualTo(Issue.Severity.WARNING);
        assertThat(report.issues().get(0).message()).contains("src/gone/**");
    }

    @Test
    void aMalformedSourcesPatternIsAnError() throws IOException {
        page("docs/a.md", "---\ntitle: A\nsources: [\"src/{a,b\"]\n---\nText\n");

        ValidationReport report = validate();

        assertThat(rules(report)).containsExactly("sources");
        assertThat(report.hasErrors()).isTrue();
    }

    @Test
    void brokenRelativeLinkIsAnErrorWithItsLineNumber() throws IOException {
        page("docs/a.md", "---\ntitle: A\n---\n# A\n\nSee [missing](nope.md) for details.\n");

        ValidationReport report = validate();

        assertThat(report.issues()).hasSize(1);
        Issue issue = report.issues().get(0);
        assertThat(issue.rule()).isEqualTo("broken-link");
        assertThat(issue.message()).contains("nope.md");
        assertThat(issue.line()).isEqualTo(6);
    }

    @Test
    void linksToExistingPagesFoldersAndFilesWork() throws IOException {
        page("docs/guides/setup.md", "# Setup\n");
        page("docs/guides/deep/index.md", "# Deep\n");
        page("docs/img/logo.png", "png");
        page("docs/a.md", """
                # A
                [same folder](guides/setup.md)
                [no extension](guides/setup)
                [folder index](guides/deep/)
                [with anchor](guides/setup.md#install)
                [image](img/logo.png)
                [up and over](../docs/guides/setup.md)
                [source file](../src/billing/Invoice.java)
                """);

        assertThat(validate().issues()).isEmpty();
    }

    @Test
    void linksThatClimbOutOfTheRepositoryAreBroken() throws IOException {
        page("docs/a.md", "# A\n[x](../../outside.md)\n");

        assertThat(rules(validate())).containsExactly("broken-link");
    }

    @Test
    void webLinksAnchorsAndSiteRoutesAreNotChecked() throws IOException {
        page("docs/a.md", """
                # A
                [web](https://example.com/nope)
                [mail](mailto:me@example.com)
                [anchor](#section)
                [route](/docs/somewhere)
                [protocol relative](//cdn.example.com/x.js)
                """);

        assertThat(validate().issues()).isEmpty();
    }

    @Test
    void linksInsideCodeAreIgnored() throws IOException {
        page("docs/a.md", """
                # A
                Use `[text](not-a-link.md)` syntax.

                ```markdown
                [example](also-not-a-link.md)
                ```

                ~~~
                [tilde](nor-this.md)
                ~~~
                """);

        assertThat(validate().issues()).isEmpty();
    }

    @Test
    void imageLinksAreCheckedToo() throws IOException {
        page("docs/a.md", "# A\n![diagram](missing.png)\n");

        assertThat(rules(validate())).containsExactly("broken-link");
    }

    @Test
    void linkCheckingIsCaseSensitiveLikeGit() throws IOException {
        page("docs/Setup.md", "# Setup\n");
        page("docs/a.md", "# A\n[x](setup.md)\n");

        assertThat(rules(validate())).containsExactly("broken-link");
    }

    @Test
    void issuesAreGroupedByPageInTheTextReport() throws IOException {
        page("docs/a.md", "# A\n[x](one.md)\n[y](two.md)\n");
        page("docs/b.md", "---\ntitle: B\n---\n");

        String text = validate().text();

        assertThat(text).contains("docs/a.md\n").contains("docs/b.md\n");
        assertThat(text).contains("broken-link").contains("'one.md' does not exist (line 2)");
        assertThat(text).endsWith("2 errors, 1 warning in 2 pages.\n");
    }

    @Test
    void jsonReportCarriesEveryField() throws IOException {
        page("docs/a.md", "# A\n[x](one.md)\n");

        assertThat(validate().json()).isEqualTo(
                "{\"pages\":1,\"errors\":1,\"warnings\":0,\"issues\":[{\"severity\":\"error\","
                        + "\"page\":\"docs/a.md\",\"line\":2,\"rule\":\"broken-link\","
                        + "\"message\":\"'one.md' does not exist\"}]}\n");
    }

    @Test
    void noDocsFolderMeansNothingToCheck() {
        ValidationReport report = validate();

        assertThat(report.pageCount()).isZero();
        assertThat(report.issues()).isEmpty();
    }
}
