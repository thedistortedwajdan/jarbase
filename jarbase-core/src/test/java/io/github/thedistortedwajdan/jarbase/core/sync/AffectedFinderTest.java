package io.github.thedistortedwajdan.jarbase.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.thedistortedwajdan.jarbase.core.content.Page;
import io.github.thedistortedwajdan.jarbase.core.content.PageParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AffectedFinderTest {

    private static Page page(String path, String... sources) {
        String yaml = sources.length == 0
                ? "title: " + path
                : "sources: [" + String.join(", ", sources) + "]";
        return PageParser.parse(path, "---\n" + yaml + "\n---\n# " + path + "\n");
    }

    private final Page billing = page("docs/billing.md", "\"src/main/java/com/acme/billing/**\"");
    private final Page users = page("docs/users.md", "\"src/main/java/com/acme/users/**\"");
    private final Page overview = page("docs/overview.md");

    @Test
    void returnsOnlyPagesWhoseSourcesChanged() {
        List<AffectedPage> result = AffectedFinder.find(
                List.of(billing, users, overview),
                List.of("src/main/java/com/acme/billing/Invoice.java"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).updated()).isFalse();
        assertThat(result.get(0).page().path()).isEqualTo("docs/billing.md");
        assertThat(result.get(0).matchedSources()).containsExactly("src/main/java/com/acme/billing/**");
        assertThat(result.get(0).changedFiles()).containsExactly("src/main/java/com/acme/billing/Invoice.java");
    }

    @Test
    void oneChangeCanAffectSeveralPages() {
        Page api = page("docs/api.md", "\"src/main/java/com/acme/**\"");

        List<AffectedPage> result = AffectedFinder.find(
                List.of(billing, api),
                List.of("src/main/java/com/acme/billing/Invoice.java"));

        assertThat(result).extracting(a -> a.page().path()).containsExactly("docs/api.md", "docs/billing.md");
    }

    @Test
    void collectsEveryMatchingFileOnce() {
        List<AffectedPage> result = AffectedFinder.find(
                List.of(billing),
                List.of("src/main/java/com/acme/billing/B.java", "src/main/java/com/acme/billing/A.java",
                        "src/main/java/com/acme/billing/A.java", "README.md"));

        assertThat(result.get(0).changedFiles()).containsExactly(
                "src/main/java/com/acme/billing/A.java", "src/main/java/com/acme/billing/B.java");
    }

    @Test
    void pageIsMarkedUpdatedWhenItsOwnFileChangedToo() {
        List<AffectedPage> result = AffectedFinder.find(
                List.of(billing, users),
                List.of("src/main/java/com/acme/billing/Invoice.java", "docs/billing.md",
                        "src/main/java/com/acme/users/User.java"));

        assertThat(result).extracting(a -> a.page().path()).containsExactly("docs/billing.md", "docs/users.md");
        assertThat(result.get(0).updated()).isTrue();
        assertThat(result.get(1).updated()).isFalse();
    }

    @Test
    void nothingChangedMeansNothingAffected() {
        assertThat(AffectedFinder.find(List.of(billing, users), List.of())).isEmpty();
        assertThat(AffectedFinder.find(List.of(billing, users), List.of("README.md"))).isEmpty();
    }

    @Test
    void pagesWithoutSourcesAreNeverAffected() {
        assertThat(AffectedFinder.find(List.of(overview), List.of("anything/at/all.txt"))).isEmpty();
    }

    @Test
    void badGlobNamesThePageInTheError() {
        Page broken = page("docs/broken.md", "\"a/{b,c\"");

        assertThatThrownBy(() -> AffectedFinder.find(List.of(broken), List.of("a/b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/broken.md");
    }
}
