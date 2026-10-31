package dev.wajdan.jarbase.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageParserTest {

    @Test
    void readsAllFrontmatterFields() {
        String md = """
                ---
                title: Billing
                description: How invoices work.
                order: 20
                sources:
                  - src/main/java/com/acme/billing/**
                  - src/main/resources/billing.yml
                tags: [billing, invoices]
                hidden: true
                ---
                # Billing

                Body text.
                """;

        Page page = PageParser.parse("billing.md", md);

        assertThat(page.path()).isEqualTo("billing.md");
        assertThat(page.frontmatter().title()).isEqualTo("Billing");
        assertThat(page.frontmatter().description()).isEqualTo("How invoices work.");
        assertThat(page.frontmatter().order()).isEqualTo(20);
        assertThat(page.frontmatter().sources())
                .containsExactly("src/main/java/com/acme/billing/**", "src/main/resources/billing.yml");
        assertThat(page.frontmatter().tags()).containsExactly("billing", "invoices");
        assertThat(page.frontmatter().hidden()).isTrue();
        assertThat(page.body()).isEqualTo("# Billing\n\nBody text.\n");
    }

    @Test
    void pageWithoutFrontmatterUsesWholeTextAsBody() {
        Page page = PageParser.parse("intro.md", "# Welcome\n\nHello.\n");

        assertThat(page.frontmatter().sources()).isEmpty();
        assertThat(page.frontmatter().hidden()).isFalse();
        assertThat(page.body()).isEqualTo("# Welcome\n\nHello.\n");
    }

    @Test
    void titleFallsBackToFirstHeading() {
        Page page = PageParser.parse("intro.md", "---\norder: 1\n---\n# Welcome\n");

        assertThat(page.frontmatter().title()).isEqualTo("Welcome");
    }

    @Test
    void explicitTitleWinsOverHeading() {
        Page page = PageParser.parse("intro.md", "---\ntitle: Start here\n---\n# Welcome\n");

        assertThat(page.frontmatter().title()).isEqualTo("Start here");
    }

    @Test
    void singleSourcesValueBecomesOneItemList() {
        Page page = PageParser.parse("a.md", "---\nsources: src/main/java/**\n---\n");

        assertThat(page.frontmatter().sources()).containsExactly("src/main/java/**");
    }

    @Test
    void windowsLineEndingsAreHandled() {
        Page page = PageParser.parse("a.md", "---\r\ntitle: A\r\nsources: [x/**]\r\n---\r\n# A\r\n");

        assertThat(page.frontmatter().title()).isEqualTo("A");
        assertThat(page.frontmatter().sources()).containsExactly("x/**");
        assertThat(page.body()).isEqualTo("# A\n");
    }

    @Test
    void emptyFrontmatterBlockIsAllowed() {
        Page page = PageParser.parse("a.md", "---\n---\n# A\n");

        assertThat(page.frontmatter().title()).isEqualTo("A");
        assertThat(page.body()).isEqualTo("# A\n");
    }

    @Test
    void unclosedFrontmatterIsTreatedAsBody() {
        Page page = PageParser.parse("a.md", "---\ntitle: A\n# no closing fence\n");

        assertThat(page.frontmatter().sources()).isEmpty();
        assertThat(page.body()).startsWith("---");
    }

    @Test
    void brokenYamlThrowsHelpfulError() {
        assertThatThrownBy(() -> PageParser.parse("bad.md", "---\ntitle: [unclosed\n---\n"))
                .isInstanceOf(InvalidFrontmatterException.class)
                .hasMessageContaining("bad.md");
    }

    @Test
    void nonNumericOrderThrows() {
        assertThatThrownBy(() -> PageParser.parse("bad.md", "---\norder: first\n---\n"))
                .isInstanceOf(InvalidFrontmatterException.class)
                .hasMessageContaining("order");
    }
}
